package com.vibecart.api.modules.chat.config;

import com.vibecart.api.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtTokenProvider tokenProvider;
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*");
    }
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");

                    if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                        String jwt = authHeader.substring(7);
                        try {
                            if (tokenProvider.validateToken(jwt)) {
                                String username = tokenProvider.getUsernameFromToken(jwt);
                                String rolesString = tokenProvider.getRolesFromToken(jwt);

                                List<SimpleGrantedAuthority> authorities = Arrays.stream(rolesString.split(","))
                                        .filter(role -> !role.trim().isEmpty())
                                        .map(SimpleGrantedAuthority::new)
                                        .collect(Collectors.toList());

                                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                        username, null, authorities
                                );

                                accessor.setUser(auth);
                                log.info("WebSocket STOMP connected successfully for user: {}", username);
                            } else {
                                log.warn("Invalid JWT Token for STOMP CONNECT — rejecting connection");
                                throw new org.springframework.messaging.MessageDeliveryException("Invalid JWT Token");
                            }
                        } catch (org.springframework.messaging.MessageDeliveryException e) {
                            throw e;
                        } catch (Exception e) {
                            log.error("Failed to authenticate STOMP connection: {}", e.getMessage());
                            throw new org.springframework.messaging.MessageDeliveryException("Authentication failed: " + e.getMessage());
                        }
                    } else {
                        log.warn("No 'Authorization' header or Bearer prefix found in STOMP CONNECT — rejecting connection");
                        throw new org.springframework.messaging.MessageDeliveryException("Missing Authorization header");
                    }
                }

                return message;
            }
        });
    }
}
