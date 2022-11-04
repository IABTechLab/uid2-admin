package com.uid2.admin.auth;

import com.uid2.shared.auth.IAuthorizableProvider;

import java.util.Collection;

public interface IAdminUserProvider extends IAuthorizableProvider {
    AdminUser getAdminUser(String token);
    AdminUser getAdminUserByContact(String contact);
    Collection<AdminUser> getAll();
}
