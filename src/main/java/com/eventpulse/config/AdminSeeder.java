package com.eventpulse.config;

import com.eventpulse.domain.Role;
import com.eventpulse.domain.User;
import com.eventpulse.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the bootstrap admin on first start so the API is usable immediately. */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public AdminSeeder(UserRepository users, PasswordEncoder encoder, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Admin admin = props.admin();
        if (admin == null || admin.email() == null || admin.email().isBlank()
                || admin.password() == null || admin.password().isBlank()) {
            return;
        }
        String email = admin.email().toLowerCase();
        if (!users.existsByEmail(email)) {
            String name = admin.name() == null || admin.name().isBlank() ? "Platform Admin" : admin.name();
            users.save(new User(name, email, encoder.encode(admin.password()), Role.ADMIN));
            log.info("Seeded bootstrap admin account {}", email);
        }
    }
}
