package ru.iopump.qa.allure.security;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iopump.qa.allure.entity.UserEntity;
import ru.iopump.qa.allure.repo.UserRepository;

/**
 * Reads {@link UserEntity} rows for Spring Security Basic auth.
 * <p>
 * Contract:
 * <ul>
 *   <li>Missing user → {@link UsernameNotFoundException}</li>
 *   <li>User with no local password (null hash, e.g. {@code guest} or OAuth-only) →
 *       {@link UsernameNotFoundException} — local login is not possible</li>
 *   <li>{@link UserEntity#isBlocked()} → account returned with {@code accountLocked=true}
 *       so Spring rejects with 401</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DbUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        final UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            log.debug("Local login refused for '{}': no password hash on record", username);
            throw new UsernameNotFoundException("No local password for user: " + username);
        }
        return User.withUsername(user.getUsername())
            .password(user.getPasswordHash())
            .accountLocked(user.isBlocked())
            .disabled(false)
            .credentialsExpired(false)
            .accountExpired(false)
            .roles(user.getRole().name())
            .build();
    }
}
