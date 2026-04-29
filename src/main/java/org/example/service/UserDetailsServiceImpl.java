package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.entity.SysUser;
import org.example.mapper.SysUserMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper sysUserMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = sysUserMapper.findByUsername(username);
        if (user == null) throw new UsernameNotFoundException("用户不存在: " + username);
        if (user.getStatus() == 0) throw new UsernameNotFoundException("账号已禁用");

        List<SimpleGrantedAuthority> authorities = user.getRoles() == null ? List.of() :
                user.getRoles().stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r.getRoleCode()))
                        .collect(Collectors.toList());

        return new User(user.getUsername(), user.getPassword(), authorities);
    }
}
