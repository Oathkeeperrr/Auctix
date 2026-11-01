package com.auctix.web;

import com.auctix.domain.Role;
import com.auctix.exception.ApiException;
import com.auctix.exception.ErrorCode;
import com.auctix.security.UserPrincipal;

/**
 * Small helpers so controllers stay free of null checks and casting.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UserPrincipal require(UserPrincipal principal) {
        if (principal == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        return principal;
    }

    public static Long idOrNull(UserPrincipal principal) {
        return principal == null ? null : principal.getId();
    }

    public static Role roleOrNull(UserPrincipal principal) {
        return principal == null ? null : principal.getRole();
    }

    public static boolean isAdmin(UserPrincipal principal) {
        return principal != null && principal.getRole() == Role.ADMIN;
    }
}
