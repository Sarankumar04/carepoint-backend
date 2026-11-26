package com.example.doctorappointment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.jwt-secret=dGVzdC1vbmx5LWRvY3Rvci1hcHBvaW50bWVudC1qd3Qta2V5",
        "app.admin-email=admin@example.test",
        "app.admin-password=TestAdminPassword21",
        "app.cancellation-cutoff=PT24H"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class DoctorAppointmentApiTest {
    private static final String PASSWORD = "InitialTestPassword21";
    private static final String DATE = "2026-12-10";
    private static final Instant INITIAL_INSTANT = Instant.parse("2026-12-07T04:00:00Z");

    @Container
    static final MySQLContainer<?> DATABASE = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("doctor_appointment_db");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired MutableClock clock;
    @MockitoBean JavaMailSender mail;

    @AfterEach
    void restoreClock() {
        clock.set(INITIAL_INSTANT);
    }

    @Test
    void registrationLoginJwtAndPasswordChanges() throws Exception {
        String email = uniqueEmail();
        var registration = send("POST", "/auth/register", null, null, registration(email));
        registration.andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        send("POST", "/auth/register", null, null, registration(email.toUpperCase(Locale.ROOT)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
        send("POST", "/auth/login", null, null, Map.of("email", email, "password", "wrong"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        String token = login(email, PASSWORD);
        send("GET", "/auth/me", token, null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
        send("GET", "/auth/me", null, null, null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        String[] parts = token.split("\\.");
        parts[2] = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        send("GET", "/auth/me", String.join(".", parts), null, null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        send("GET", "/admin/dashboard/summary", token, null, null).andExpect(status().isForbidden());

        clock.set(INITIAL_INSTANT.plus(Duration.ofMinutes(31)));
        send("GET", "/auth/me", token, null, null).andExpect(status().isUnauthorized());
        String validToken = login(email, PASSWORD);
        send("PATCH", "/auth/change-password", validToken, null,
                Map.of("currentPassword", PASSWORD, "newPassword", "ReplacementPassword21"))
                .andExpect(status().isNoContent());
        send("GET", "/auth/me", validToken, null, null).andExpect(status().isUnauthorized());
        send("GET", "/auth/me", login(email, "ReplacementPassword21"), null, null).andExpect(status().isOk());
    }

    @Test
    void reactRegistrationAndCorsUseTheConfiguredOrigin() throws Exception {
        var preflight = request(HttpMethod.OPTIONS, "/api/v1/auth/register")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type");
        mvc.perform(preflight).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
        mvc.perform(request(HttpMethod.OPTIONS, "/api/v1/auth/register")
                        .header(HttpHeaders.ORIGIN, "https://other.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());

        String email = uniqueEmail();
        send("POST", "/auth/register", null, null, Map.of("fullName", "React Patient", "email", email,
                "password", PASSWORD, "phone", "9876543210")).andExpect(status().isCreated());
        String token = login(email, PASSWORD);
        send("GET", "/patients/me", token, null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("React Patient"));
        send("POST", "/auth/register", null, null, Map.of("email", "invalid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.email").isString());
    }

    @Test
    void passwordRecoveryEmailsSingleUseTokenAndKeepsTheResponseGeneric() throws Exception {
        Patient patient = patient();
        var unknown = body(send("POST", "/auth/forgot-password", null, null, Map.of("email", uniqueEmail()))
                .andExpect(status().isOk()));
        var known = body(send("POST", "/auth/forgot-password", null, null, Map.of("email", patient.email()))
                .andExpect(status().isOk()));
        assertThat(known).isEqualTo(unknown);
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, timeout(5000)).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly(patient.email());
        String resetLink = Arrays.stream(message.getValue().getText().split("\\R"))
                .filter(line -> line.contains("?token=")).findFirst().orElseThrow();
        String token = UriComponentsBuilder.fromUriString(resetLink).build().getQueryParams().getFirst("token");
        var replacement = Map.of("token", token, "newPassword", "RecoveredPassword21");
        send("POST", "/auth/reset-password", null, null, replacement).andExpect(status().isNoContent());
        send("POST", "/auth/reset-password", null, null, replacement)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
        send("GET", "/auth/me", patient.token(), null, null).andExpect(status().isUnauthorized());
        login(patient.email(), "RecoveredPassword21");
    }

    @Test
    void directoryProfilesAndDeactivationEnforceRoleAndIdentity() throws Exception {
        Fixture f = fixture();
        send("GET", "/departments", null, null, null).andExpect(status().isOk());
        send("GET", "/departments/{id}", f.admin, f.departmentId, null).andExpect(status().isOk());
        send("POST", "/departments", f.patient.token(), null, Map.of("name", "Forbidden"))
                .andExpect(status().isForbidden());
        send("PUT", "/departments/{id}", f.admin, f.departmentId, Map.of("name", "Renamed " + UUID.randomUUID()))
                .andExpect(status().isOk());
        send("GET", "/doctors?departmentId=" + f.departmentId + "&specialization=Intern", f.patient.token(), null, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(f.doctorId));
        send("GET", "/doctors/{id}", null, f.doctorId, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.qualification").value("MD"));
        send("PUT", "/doctors/{id}", f.admin, f.doctorId, Map.of("fullName", "Updated Doctor",
                "departmentId", f.departmentId, "specialization", "Internal Medicine", "consultationFee", 650))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Updated Doctor"));
        send("GET", "/doctors/me", f.doctorToken, null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(f.doctorEmail))
                .andExpect(jsonPath("$.phone").value("9876543210"));
        send("GET", "/doctors/me", null, null, null).andExpect(status().isUnauthorized());
        send("PUT", "/doctors/me", f.doctorToken, null, Map.of("fullName", "New Doctor", "bio", "Clinic doctor"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bio").value("Clinic doctor"));
        send("GET", "/doctors/me", f.patient.token(), null, null).andExpect(status().isForbidden());
        send("PATCH", "/departments/{id}/status", f.admin, f.departmentId, Map.of("active", false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        send("GET", "/doctors/{id}", f.patient.token(), f.doctorId, null).andExpect(status().isNotFound());
        send("PATCH", "/doctors/{id}/status", f.admin, f.doctorId, Map.of("active", false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        send("GET", "/auth/me", f.doctorToken, null, null).andExpect(status().isUnauthorized());

        send("GET", "/patients/me", f.patient.token(), null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(f.patient.id()));
        send("PUT", "/patients/me", f.patient.token(), null, Map.of("fullName", "Updated Patient",
                "phone", "9876543210", "dateOfBirth", "2000-05-20", "address", "Coimbatore"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Updated Patient"));
        send("GET", "/patients", f.admin, null, null).andExpect(status().isOk());
        send("GET", "/patients/{id}", f.admin, f.patient.id(), null).andExpect(status().isOk());
        send("GET", "/patients/{id}", f.patient.token(), f.patient.id(), null).andExpect(status().isForbidden());
        send("PATCH", "/patients/{id}/status", f.admin, f.patient.id(), Map.of("active", false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        send("GET", "/auth/me", f.patient.token(), null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void windowValidationAndAppointmentLifecyclePreserveOwnershipAndOriginalSlot() throws Exception {
        Fixture f = fixture();
        Patient other = patient();
        long windowId = f.windowId;
        send("GET", "/doctors/me/availability", f.doctorToken, null, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(windowId));
        send("POST", "/doctors/me/availability", f.doctorToken, null, window("10:30", "12:00", 30))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AVAILABILITY_OVERLAP"));
        send("POST", "/doctors/me/availability", f.doctorToken, null, window("12:00", "12:20", 15))
                .andExpect(status().isBadRequest());
        send("PUT", "/doctors/me/availability/{id}", f.doctorToken, windowId, window("10:00", "12:00", 30))
                .andExpect(status().isOk());
        send("GET", "/doctors/" + f.doctorId + "/slots?date=" + DATE, f.patient.token(), null, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
        send("POST", "/appointments", f.patient.token(), null, booking(f.doctorId, "10:15"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SLOT"));
        var spoofed = new HashMap<>(booking(f.doctorId, "10:00"));
        spoofed.put("patientId", other.id());
        send("POST", "/appointments", f.patient.token(), null, spoofed).andExpect(status().isBadRequest());
        long first = id(send("POST", "/appointments", f.patient.token(), null, booking(f.doctorId, "10:00"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.patientId").value(f.patient.id())));
        long second = id(send("POST", "/appointments", other.token(), null, booking(f.doctorId, "10:30"))
                .andExpect(status().isCreated()));
        send("GET", "/appointments/{id}", other.token(), first, null).andExpect(status().isNotFound());
        send("GET", "/appointments/{id}", f.doctorToken, first, null).andExpect(status().isOk());
        send("GET", "/appointments/my?status=CONFIRMED&fromDate=" + DATE, f.patient.token(), null, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        send("GET", "/appointments/doctor/my", f.doctorToken, null, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        send("GET", "/admin/appointments?doctorId=" + f.doctorId + "&patientId=" + other.id(), f.admin, null, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(second));
        send("GET", "/admin/appointments/{id}", f.admin, first, null).andExpect(status().isOk());
        send("GET", "/admin/dashboard/summary", f.admin, null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentsByStatus.CONFIRMED").isNumber());
        send("PATCH", "/appointments/{id}/reschedule", f.patient.token(), first, move("10:30"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPOINTMENT_SLOT_UNAVAILABLE"));
        send("GET", "/appointments/{id}", f.patient.token(), first, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.startTime").value("10:00:00"));
        send("PATCH", "/appointments/{id}/reschedule", f.patient.token(), first, move("11:00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.startTime").value("11:00:00"));
        send("PATCH", "/admin/appointments/{id}/status", f.admin, first, Map.of("status", "CONFIRMED"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_APPOINTMENT_TRANSITION"));
        send("DELETE", "/doctors/me/availability/{id}", f.doctorToken, windowId, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AVAILABILITY_HAS_BOOKINGS"));
        send("PATCH", "/appointments/{id}/cancel", other.token(), first, Map.of("cancellationReason", "Conflict"))
                .andExpect(status().isNotFound());
        send("PATCH", "/appointments/{id}/cancel", f.patient.token(), first, Map.of("cancellationReason", "Conflict"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isString());
        send("PATCH", "/admin/appointments/{id}/status", f.admin, second, Map.of("status", "CANCELLED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cancellationReason").value("Cancelled by the clinic."));
        send("DELETE", "/doctors/me/availability/{id}", f.doctorToken, windowId, null).andExpect(status().isNoContent());

        send("GET", "/doctors/me/availability", f.doctorToken, null, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));

        var notifications = body(send("GET", "/notifications", f.patient.token(), null, null)
                .andExpect(status().isOk()));
        assertThat(notifications.path("totalElements").asInt()).isEqualTo(3);
        long notice = notifications.path("content").get(0).path("id").asLong();
        send("PATCH", "/notifications/{id}/read", other.token(), notice, null).andExpect(status().isNotFound());
        send("PATCH", "/notifications/{id}/read", f.patient.token(), notice, null).andExpect(status().isNoContent());
        send("PATCH", "/notifications/read-all", f.patient.token(), null, null).andExpect(status().isNoContent());
        var allRead = body(send("GET", "/notifications", f.patient.token(), null, null).andExpect(status().isOk()));
        allRead.path("content").forEach(n -> assertThat(n.path("readAt").isTextual()).isTrue());
    }

    @Test
    void cutoffAndDoctorStatusTransitions() throws Exception {
        Fixture f = fixture();
        long completed = id(send("POST", "/appointments", f.patient.token(), null, booking(f.doctorId, "10:00"))
                .andExpect(status().isCreated()));
        long noShow = id(send("POST", "/appointments", f.patient.token(), null, booking(f.doctorId, "10:30"))
                .andExpect(status().isCreated()));
        send("GET", "/appointments/{id}", f.patient.token(), completed, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.allowedStatuses[0]").value("CANCELLED"));
        send("GET", "/appointments/{id}", f.doctorToken, completed, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.allowedStatuses").isEmpty());
        clock.set(Instant.parse("2026-12-10T04:00:00Z"));
        String patientToken = login(f.patient.email(), PASSWORD);
        send("GET", "/appointments/{id}", patientToken, completed, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.allowedStatuses").isEmpty());
        send("PATCH", "/appointments/{id}/cancel", patientToken, completed, Map.of("cancellationReason", "Late"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANCELLATION_CUTOFF_PASSED"));
        clock.set(Instant.parse("2026-12-10T06:00:00Z"));
        String doctorToken = login(f.doctorEmail, PASSWORD);
        send("GET", "/appointments/{id}", doctorToken, completed, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedStatuses").value(org.hamcrest.Matchers.contains("COMPLETED", "NO_SHOW")));
        send("PATCH", "/appointments/{id}/complete", doctorToken, completed, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.completedAt").isString())
                .andExpect(jsonPath("$.allowedStatuses").isEmpty());
        send("PATCH", "/appointments/{id}/no-show", doctorToken, noShow, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_SHOW"));
        send("PATCH", "/appointments/{id}/no-show", doctorToken, completed, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_APPOINTMENT_TRANSITION"));
    }

    @Test
    void competingRequestsCommitOnlyOneBooking() throws Exception {
        Fixture f = fixture();
        Patient other = patient();
        CyclicBarrier ready = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = workers.submit(() -> competingBooking(ready, f.patient.token(), f.doctorId));
            Future<Integer> second = workers.submit(() -> competingBooking(ready, other.token(), f.doctorId));
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        send("GET", "/appointments/doctor/my", f.doctorToken, null, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        send("GET", "/notifications", f.doctorToken, null, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void swaggerPublishesAllExpectedApiOperations() throws Exception {
        var ui = http.getForEntity("/swagger-ui/index.html", String.class);
        assertThat(ui.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(ui.getBody()).contains("Swagger UI");
        var specResponse = http.getForEntity("/v3/api-docs", String.class);
        assertThat(specResponse.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode specs = json.readTree(specResponse.getBody());
        JsonNode paths = specs.path("paths");
        Map<String, String> expected = Map.ofEntries(
                Map.entry("/auth/register", "post"), Map.entry("/auth/login", "post"),
                Map.entry("/auth/me", "get"), Map.entry("/auth/forgot-password", "post"),
                Map.entry("/auth/reset-password", "post"), Map.entry("/auth/change-password", "patch"),
                Map.entry("/departments", "get post"), Map.entry("/departments/{departmentId}", "get put"),
                Map.entry("/departments/{departmentId}/status", "patch"), Map.entry("/doctors", "get post"),
                Map.entry("/doctors/{doctorId}", "get put"), Map.entry("/doctors/{doctorId}/status", "patch"),
                Map.entry("/doctors/me", "get put"), Map.entry("/patients/me", "get put"),
                Map.entry("/patients", "get"), Map.entry("/patients/{patientId}", "get"),
                Map.entry("/patients/{patientId}/status", "patch"), Map.entry("/doctors/me/availability", "get post"),
                Map.entry("/doctors/me/availability/{availabilityId}", "put delete"),
                Map.entry("/doctors/{doctorId}/slots", "get"), Map.entry("/appointments", "post"),
                Map.entry("/appointments/my", "get"), Map.entry("/appointments/doctor/my", "get"),
                Map.entry("/appointments/{appointmentId}", "get"), Map.entry("/appointments/{appointmentId}/cancel", "patch"),
                Map.entry("/appointments/{appointmentId}/reschedule", "patch"),
                Map.entry("/appointments/{appointmentId}/complete", "patch"),
                Map.entry("/appointments/{appointmentId}/no-show", "patch"),
                Map.entry("/admin/dashboard/summary", "get"), Map.entry("/admin/appointments", "get"),
                Map.entry("/admin/appointments/{appointmentId}", "get"),
                Map.entry("/admin/appointments/{appointmentId}/status", "patch"),
                Map.entry("/notifications", "get"), Map.entry("/notifications/{notificationId}/read", "patch"),
                Map.entry("/notifications/read-all", "patch"));
        assertThat(paths.size()).isEqualTo(expected.size());
        expected.forEach((path, methods) -> {
            JsonNode operations = paths.path("/api/v1" + path);
            assertThat(operations.size()).as(path).isEqualTo(methods.split(" ").length);
            for (String method : methods.split(" ")) {
                assertThat(operations.has(method)).as("%s %s", method, path).isTrue();
            }
        });
        assertThat(specs.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        assertThat(paths.path("/api/v1/auth/login").path("post").path("security").isArray()).isTrue();
        assertThat(paths.path("/api/v1/auth/login").path("post").path("security").size()).isZero();
        List<String> appointmentFilters = new ArrayList<>();
        paths.path("/api/v1/admin/appointments").path("get").path("parameters")
                .forEach(parameter -> appointmentFilters.add(parameter.path("name").asText()));
        assertThat(appointmentFilters).containsExactlyInAnyOrder(
                "doctorId", "patientId", "status", "date", "fromDate", "toDate", "page", "size");
    }

    private int competingBooking(CyclicBarrier ready, String token, long doctorId) throws Exception {
        ready.await(10, TimeUnit.SECONDS);
        return send("POST", "/appointments", token, null, booking(doctorId, "10:00"))
                .andReturn().getResponse().getStatus();
    }

    private Fixture fixture() throws Exception {
        String admin = login("admin@example.test", "TestAdminPassword21");
        long department = id(send("POST", "/departments", admin, null, Map.of("name", "Dept " + UUID.randomUUID()))
                .andExpect(status().isCreated()));
        String doctorEmail = uniqueEmail();
        long doctor = id(send("POST", "/doctors", admin, null, Map.of("fullName", "Dr Test", "email", doctorEmail,
                "password", PASSWORD, "departmentId", department, "specialization", "Internal Medicine", "consultationFee", 500,
                "phone", "9876543210", "qualification", "MD", "yearsOfExperience", 10))
                .andExpect(status().isCreated()));
        String doctorToken = login(doctorEmail, PASSWORD);
        long window = id(send("POST", "/doctors/me/availability", doctorToken, null, window("10:00", "11:30", 30))
                .andExpect(status().isCreated()));
        return new Fixture(admin, department, doctor, doctorEmail, doctorToken, window, patient());
    }

    private Patient patient() throws Exception {
        String email = uniqueEmail();
        send("POST", "/auth/register", null, null, registration(email)).andExpect(status().isCreated());
        String token = login(email, PASSWORD);
        long id = id(send("GET", "/patients/me", token, null, null).andExpect(status().isOk()));
        return new Patient(id, email, token);
    }

    private static Map<String, Object> registration(String email) {
        return Map.of("fullName", "Alex Kumar", "email", email, "password", PASSWORD, "phone", "9876543210",
                "dateOfBirth", "2000-05-20", "gender", "MALE", "address", "Coimbatore");
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@example.test";
    }

    private static Map<String, Object> window(String start, String end, int minutes) {
        return Map.of("date", DATE, "startTime", start, "endTime", end, "slotDurationMinutes", minutes);
    }

    private static Map<String, Object> booking(long doctorId, String start) {
        return Map.of("doctorId", doctorId, "appointmentDate", DATE, "startTime", start, "reason", "Checkup");
    }

    private static Map<String, Object> move(String start) {
        return Map.of("appointmentDate", DATE, "startTime", start);
    }

    private String login(String email, String password) throws Exception {
        return body(send("POST", "/auth/login", null, null, Map.of("email", email, "password", password))
                .andExpect(status().isOk())).path("accessToken").asText();
    }

    private long id(ResultActions result) throws Exception {
        return body(result).path("id").asLong();
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }

    private ResultActions send(String method, String path, String bearer, Long id, Object payload) throws Exception {
        String url = "/api/v1" + path;
        MockHttpServletRequestBuilder builder = id == null ? request(HttpMethod.valueOf(method), url)
                : request(HttpMethod.valueOf(method), url, id);
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        if (payload != null) {
            builder.contentType("application/json").content(json.writeValueAsBytes(payload));
        }
        return mvc.perform(builder);
    }

    private record Fixture(String admin, long departmentId, long doctorId, String doctorEmail, String doctorToken,
                           long windowId, Patient patient) {}
    private record Patient(long id, String email, String token) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        private volatile Instant now = INITIAL_INSTANT;

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Kolkata");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
