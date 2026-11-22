package com.finance.gateway.client;

import com.finance.graphqlapi.dto.GraphQLView.AuthPayload;
import com.finance.graphqlapi.dto.GraphQLView.UserView;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthServiceClient {
    AuthPayload login(String email, String password);
    AuthPayload register(String email, String password, String fullName);
    void logout(String token);
    Optional<UserView> validate(String token);
    List<UserView> users(String token);
    UserView user(String token, UUID id);
}
