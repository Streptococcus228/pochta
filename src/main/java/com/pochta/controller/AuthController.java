package com.pochta.controller;

import com.pochta.dto.LoginRequest;
import com.pochta.dto.RegisterRequest;
import com.pochta.model.User;
import com.pochta.service.AuthService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request, HttpSession session) {
        User user = authService.register(request.getUsername(), request.getPassword(),
                request.getFullName(), request.getEmail());

        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Пользователь с таким логином уже существует"));
        }

        // Сохраняем пользователя в сессию
        session.setAttribute("currentUser", user);
        session.setAttribute("userId", user.getId());

        return ResponseEntity.ok(Map.of(
                "message", "Регистрация успешна!",
                "userId", user.getId(),
                "username", user.getUsername(),
                "fullName", user.getFullName()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpSession session) {
        var userOpt = authService.login(request.getUsername(), request.getPassword());

        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Неверный логин или пароль"));
        }

        User user = userOpt.get();

        // Сохраняем пользователя в сессию
        session.setAttribute("currentUser", user);
        session.setAttribute("userId", user.getId());

        return ResponseEntity.ok(Map.of(
                "message", "Вход выполнен успешно!",
                "userId", user.getId(),
                "username", user.getUsername(),
                "fullName", user.getFullName()
        ));
    }
}