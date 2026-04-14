package com.pochta.service;

import com.pochta.model.User;
import com.pochta.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    /**
     * Реєстрація звичайного користувача
     */
    public User register(String username, String password, String fullName, String email) {
        if (userRepository.existsByUsername(username)) {
            return null; // користувач вже існує
        }

        User user = User.builder()
                .username(username)
                .password(password)
                .fullName(fullName)
                .email(email)
                .role("USER")           // звичайний користувач
                .build();

        return userRepository.save(user);
    }

    /**
     * Логін користувача
     */
    public Optional<User> login(String username, String password) {
        return userRepository.findByUsername(username)
                .filter(user -> user.getPassword().equals(password));
    }

    /**
     * Створення адміністратора при першому запуску
     */
    @PostConstruct
    public void createAdminIfNotExists() {
        if (!userRepository.existsByUsername("admin")) {
            User admin = User.builder()
                    .username("admin")
                    .password("admin123")
                    .fullName("Системний Адміністратор")
                    .email("admin@pochta.ua")
                    .role("ADMIN")
                    .build();

            userRepository.save(admin);
            System.out.println("✅ Адміністратор успішно створений!");
            System.out.println("   Логін: admin");
            System.out.println("   Пароль: admin123");
        } else {
            System.out.println("✅ Адміністратор вже існує в базі");
        }
    }
}