package org.example.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@SuppressWarnings("deprecation") // for Spring Boot 2.x
@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .csrf().disable() // Disable CSRF for APIs
                .authorizeRequests()
                .antMatchers("/auth/**").permitAll() // Allow login and register
                .antMatchers("/emails/send").permitAll()     // allow sending for now
                .antMatchers("/emails/inbox/**").permitAll() // allow viewing inbox
                .antMatchers("/emails/**").permitAll() // 🔥 ADD THIS LINE
                .anyRequest().authenticated()       // All others require auth
                .and()
                .httpBasic(); // Or you can disable completely if unused
    }
}
