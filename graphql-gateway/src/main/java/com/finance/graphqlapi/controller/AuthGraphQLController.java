package com.finance.graphqlapi.controller;

import com.finance.gateway.client.AuthServiceClient;
import com.finance.gateway.security.AuthenticatedUser;
import com.finance.graphqlapi.dto.GraphQLInput;
import com.finance.graphqlapi.dto.GraphQLView.AuthPayload;
import com.finance.graphqlapi.dto.GraphQLView.UserView;
import jakarta.validation.Valid;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.UUID;

@Controller
@Validated
public class AuthGraphQLController {
    private final AuthServiceClient auth;

    public AuthGraphQLController(AuthServiceClient auth) {
        this.auth = auth;
    }

    @MutationMapping
    public AuthPayload login(@Argument String email, @Argument String password) {
        return auth.login(email, password);
    }

    @MutationMapping
    public AuthPayload register(@Argument @Valid GraphQLInput.RegisterInput input) {
        return auth.register(input.email(), input.password(), input.fullName());
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public boolean logout() {
        auth.logout(AuthenticatedUser.current().token());
        return true;
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public UserView me() {
        return AuthenticatedUser.current().user();
    }

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserView> users() {
        return auth.users(AuthenticatedUser.current().token());
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public UserView user(@Argument UUID id) {
        AuthenticatedUser current = AuthenticatedUser.current();
        if (!"ADMIN".equals(current.role()) && !current.id().equals(id)) {
            throw new AccessDeniedException("Можно запрашивать только собственный профиль");
        }
        return auth.user(current.token(), id);
    }
}
