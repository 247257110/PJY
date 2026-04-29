package org.example.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.example.config.JwtUtil;
import org.example.entity.SysOrg;
import org.example.entity.SysUser;
import org.example.mapper.SysMenuMapper;
import org.example.mapper.SysOrgMapper;
import org.example.mapper.SysUserMapper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final SysUserMapper sysUserMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysOrgMapper sysOrgMapper;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        Map<String, Object> result = new HashMap<>();
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
            String token = jwtUtil.generate(req.getUsername());
            SysUser user = sysUserMapper.findByUsername(req.getUsername());

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("username", user.getUsername());
            userInfo.put("realName", user.getRealName());
            userInfo.put("roles", user.getRoles() == null ? List.of() :
                    user.getRoles().stream().map(r -> r.getRoleCode()).collect(Collectors.toList()));
            userInfo.put("menuKeys", sysMenuMapper.findMenuKeysByUserId(user.getId()));
            userInfo.put("orgId", user.getOrgId());
            if (user.getOrgId() != null) {
                SysOrg org = sysOrgMapper.findById(user.getOrgId());
                userInfo.put("orgName", org != null ? org.getOrgName() : null);
            } else {
                userInfo.put("orgName", null);
            }

            result.put("code", 200);
            result.put("token", token);
            result.put("user", userInfo);
        } catch (AuthenticationException e) {
            result.put("code", 401);
            result.put("message", "用户名或密码错误");
        }
        return result;
    }

    @GetMapping("/info")
    public Map<String, Object> info(Authentication auth) {
        Map<String, Object> result = new HashMap<>();
        SysUser user = sysUserMapper.findByUsername(auth.getName());
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("realName", user.getRealName());
        userInfo.put("roles", user.getRoles() == null ? List.of() :
                user.getRoles().stream().map(r -> r.getRoleCode()).collect(Collectors.toList()));
        userInfo.put("menuKeys", sysMenuMapper.findMenuKeysByUserId(user.getId()));
        userInfo.put("orgId", user.getOrgId());
        if (user.getOrgId() != null) {
            SysOrg org = sysOrgMapper.findById(user.getOrgId());
            userInfo.put("orgName", org != null ? org.getOrgName() : null);
        } else {
            userInfo.put("orgName", null);
        }
        result.put("code", 200);
        result.put("user", userInfo);
        return result;
    }

    @Data
    static class LoginRequest {
        private String username;
        private String password;
    }
}
