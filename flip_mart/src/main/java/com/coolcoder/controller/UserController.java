package com.coolcoder.controller;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.coolcoder.model.Cart;
import com.coolcoder.model.Category;
import com.coolcoder.model.OrderRequest;
import com.coolcoder.model.ProductOrder;
import com.coolcoder.model.UserDtls;
import com.coolcoder.repository.UserRepository;
import com.coolcoder.service.CartService;
import com.coolcoder.service.CategoryService;
import com.coolcoder.service.OrderService;
import com.coolcoder.service.UserService;
import com.coolcoder.util.CommonUtil;
import com.coolcoder.util.OrderStatus;
import com.coolcoder.util.RazorpaySignatureUtil;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/user")
public class UserController {
	@Autowired
	private UserService userService;
	@Autowired
	private CategoryService categoryService;

	@Autowired
	private CartService cartService;

	@Autowired
	private OrderService orderService;

	@Autowired
	private CommonUtil commonUtil;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Value("${razorpay.key.id:}")
	private String razorpayKeyId;

	@Value("${razorpay.key.secret:}")
	private String razorpayKeySecret;

	@Value("${razorpay.currency:INR}")
	private String razorpayCurrency;

	@Value("${razorpay.company.name:Ecom Store}")
	private String razorpayCompanyName;


	@GetMapping("/")
	public String home() {
		return "user/home";
	}

	@ModelAttribute
	public void getUserDetails(Principal p, Model m) {
		if (p != null) {
			String email = p.getName();
			UserDtls userDtls = userService.getUserByEmail(email);
			m.addAttribute("user", userDtls);
			Integer countCart = cartService.getCountCart(userDtls.getId());
			m.addAttribute("countCart", countCart);
		}

		List<Category> allActiveCategory = categoryService.getAllActiveCategory();
		m.addAttribute("categorys", allActiveCategory);
	}

	@GetMapping("/addCart")
	public String addToCart(@RequestParam Integer pid, @RequestParam Integer uid, HttpSession session) {
		Cart saveCart = cartService.saveCart(pid, uid);

		if (ObjectUtils.isEmpty(saveCart)) {
			session.setAttribute("errorMsg", "Product add to cart failed");
		} else {
			session.setAttribute("succMsg", "Product added to cart");
		}
		return "redirect:/product/" + pid;
	}

	@GetMapping("/cart")
	public String loadCartPage(Principal p, Model m) {

		UserDtls user = getLoggedInUserDetails(p);
		List<Cart> carts = cartService.getCartsByUser(user.getId());
		m.addAttribute("carts", carts);
		if (carts.size() > 0) {
			Double totalOrderPrice = carts.get(carts.size() - 1).getTotalOrderPrice();
			m.addAttribute("totalOrderPrice", totalOrderPrice);
		}
		return "/user/cart";
	}

	@GetMapping("/cartQuantityUpdate")
	public String updateCartQuantity(@RequestParam String sy, @RequestParam Integer cid) {
		cartService.updateQuantity(sy, cid);
		return "redirect:/user/cart";
	}

	private UserDtls getLoggedInUserDetails(Principal p) {
		String email = p.getName();
		UserDtls userDtls = userService.getUserByEmail(email);
		return userDtls;
	}

	@GetMapping("/orders")
	public String orderPage(Principal p, Model m) {
		UserDtls user = getLoggedInUserDetails(p);
		List<Cart> carts = cartService.getCartsByUser(user.getId());
		m.addAttribute("carts", carts);
		if (carts.size() > 0) {
			Double orderPrice = carts.get(carts.size() - 1).getTotalOrderPrice();
			Double totalOrderPrice = carts.get(carts.size() - 1).getTotalOrderPrice() + 250 + 100;
			m.addAttribute("orderPrice", orderPrice);
			m.addAttribute("totalOrderPrice", totalOrderPrice);
			m.addAttribute("amountInPaise", Math.round(totalOrderPrice * 100));
		}
		m.addAttribute("razorpayKeyId", razorpayKeyId);
		m.addAttribute("razorpayEnabled", !ObjectUtils.isEmpty(razorpayKeyId) && !ObjectUtils.isEmpty(razorpayKeySecret));
		m.addAttribute("razorpayCompanyName", razorpayCompanyName);
		return "/user/order";
	}

	@PostMapping("/create-razorpay-order")
	@ResponseBody
	public ResponseEntity<?> createRazorpayOrder(Principal p) {
	    if (p == null) {
	        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
	                .body(Map.of("message", "Please login first"));
	    }

	    UserDtls user = getLoggedInUserDetails(p);
	    if (user == null) {
	        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
	                .body(Map.of("message", "User not found"));
	    }

	    List<Cart> carts = cartService.getCartsByUser(user.getId());
	    if (carts == null || carts.isEmpty()) {
	        return ResponseEntity.badRequest()
	                .body(Map.of("message", "Your cart is empty"));
	    }

	    if (ObjectUtils.isEmpty(razorpayKeyId) || ObjectUtils.isEmpty(razorpayKeySecret)) {
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                .body(Map.of("message", "Razorpay keys are not configured"));
	    }

	    try {
	        double cartTotal = carts.stream()
	                .mapToDouble(Cart::getTotalOrderPrice)
	                .sum();

	        double total = cartTotal + 250 + 100;
	        long amountInPaise = Math.round(total * 100);

	        RazorpayClient razorpay = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

	        JSONObject options = new JSONObject();
	        options.put("amount", amountInPaise);
	        options.put("currency", razorpayCurrency);
	        options.put("receipt", "rcpt_" + System.currentTimeMillis());

	        Order order = razorpay.orders.create(options);

	        Map<String, Object> payload = new LinkedHashMap<>();
	        payload.put("id", order.get("id"));
	        payload.put("amount", order.get("amount"));
	        payload.put("currency", order.get("currency"));
	        payload.put("key", razorpayKeyId);
	        payload.put("name", razorpayCompanyName);
	        payload.put("email", user.getEmail());
	        payload.put("contact", user.getMobileNumber());

	        return ResponseEntity.ok(payload);

	    } catch (Exception e) {
	        e.printStackTrace();
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                .body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Unable to create Razorpay order"));
	    }
	}

	@PostMapping("/save-order")
	public String saveOrder(@ModelAttribute OrderRequest request, Principal p,
	        RedirectAttributes redirectAttributes) throws Exception {

	    UserDtls user = getLoggedInUserDetails(p);

	    if ("ONLINE".equalsIgnoreCase(request.getPaymentType())) {
	        boolean valid = RazorpaySignatureUtil.verifySignature(
	                request.getRazorpayOrderId(),
	                request.getRazorpayPaymentId(),
	                request.getRazorpaySignature(),
	                razorpayKeySecret);

	        if (!valid) {
	            redirectAttributes.addFlashAttribute("errorMsg",
	                    "Online payment verification failed. Please try again.");
	            return "redirect:/user/orders";
	        }
	    }

	    orderService.saveOrder(user.getId(), request);
	    redirectAttributes.addFlashAttribute("succMsg", "Order placed successfully");
	    return "redirect:/user/success";
	}

	@GetMapping("/success")
	public String loadSuccess() {
		return "/user/success";
	}

	@GetMapping("/user-orders")
	public String myOrder(Model m, Principal p) {
		UserDtls loginUser = getLoggedInUserDetails(p);
		List<ProductOrder> orders = orderService.getOrdersByUser(loginUser.getId());
		m.addAttribute("orders", orders);
		return "/user/my_orders";
	}

	@GetMapping("/update-status")
	public String updateOrderStatus(@RequestParam Integer id, @RequestParam Integer st, HttpSession session) {

		OrderStatus[] values = OrderStatus.values();
		String status = null;

		for (OrderStatus orderSt : values) {
			if (orderSt.getId().equals(st)) {
				status = orderSt.getName();
			}
		}

		ProductOrder updateOrder = orderService.updateOrderStatus(id, status);
		
		try {
			commonUtil.sendMailForProductOrder(updateOrder, status);
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (!ObjectUtils.isEmpty(updateOrder)) {
			session.setAttribute("succMsg", "Status Updated");
		} else {
			session.setAttribute("errorMsg", "status not updated");
		}
		return "redirect:/user/user-orders";
	}

	@GetMapping("/profile")
	public String profile() {
		return "/user/profile";
	}

	@PostMapping("/update-profile")
	public String updateProfile(@ModelAttribute UserDtls user, @RequestParam MultipartFile img, HttpSession session) {
		UserDtls updateUserProfile = userService.updateUserProfile(user, img);
		if (ObjectUtils.isEmpty(updateUserProfile)) {
			session.setAttribute("errorMsg", "Profile not updated");
		} else {
			session.setAttribute("succMsg", "Profile Updated");
		}
		return "redirect:/user/profile";
	}

	@PostMapping("/change-password")
	public String changePassword(@RequestParam String newPassword, @RequestParam String currentPassword, Principal p,
			HttpSession session) {
		UserDtls loggedInUserDetails = getLoggedInUserDetails(p);

		boolean matches = passwordEncoder.matches(currentPassword, loggedInUserDetails.getPassword());

		if (matches) {
			String encodePassword = passwordEncoder.encode(newPassword);
			loggedInUserDetails.setPassword(encodePassword);
			UserDtls updateUser = userService.updateUser(loggedInUserDetails);
			if (ObjectUtils.isEmpty(updateUser)) {
				session.setAttribute("errorMsg", "Password not updated !! Error in server");
			} else {
				session.setAttribute("succMsg", "Password Updated sucessfully");
			}
		} else {
			session.setAttribute("errorMsg", "Current Password incorrect");
		}

		return "redirect:/user/profile";
	}

}
