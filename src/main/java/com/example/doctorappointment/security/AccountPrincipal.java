package com.example.doctorappointment.security;

import com.example.doctorappointment.enums.Role;

public record AccountPrincipal(long userId, Role role) {}
