package com.coolcoder.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Replaces Spring Boot Whitelabel pages with friendly UI pages.
 */
@Controller
public class AppErrorController implements ErrorController {

	@GetMapping("/access-denied")
	public String accessDenied() {
		return "access_denied";
	}

	@RequestMapping("/error")
	public String error() {
		return "error";
	}
}
