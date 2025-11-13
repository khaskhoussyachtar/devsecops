package com.example.demo.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;

@Controller
public class LoginController {

    // Show login page
    @GetMapping("/login")
    public String loginPage() {
        return "login.html"; // returns login.html
    }

    // Handle login form submission
    @PostMapping("/doLogin")
    public String doLogin(@RequestParam String username,
                          @RequestParam String password,
                          HttpSession session) {

        // Simple hardcoded authentication (for demo)
        if("admin".equals(username) && "1234".equals(password)) {
            session.setAttribute("user", username);
            return "redirect:/index.html"; // redirect to home page after login
        }

        // failed login
        return "redirect:/login";
    }

    // Example home page
    @GetMapping("/")
    public String home(HttpSession session) {
        Object user = session.getAttribute("user");
        if(user == null) {
            return "redirect:/login"; // not logged in
        }
        return "index"; // your static index.html (move to templates if needed)
    }
}
