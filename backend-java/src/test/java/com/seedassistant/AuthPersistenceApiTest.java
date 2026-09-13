package com.seedassistant;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="RUN_MYSQL_TESTS", matches="true")
class AuthPersistenceApiTest extends JwtTestSupport {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    private final List<String> ids = new ArrayList<>();
    private final List<String> unowned = new ArrayList<>();
    private final String password = "synthetic-" + UUID.randomUUID();
    @AfterEach void cleanup() {
        for (String id : unowned) jdbc.update("DELETE FROM consultation_session WHERE id=?", id);
        for (String id : ids) {
            jdbc.update("DELETE FROM consultation_session WHERE user_id=?", id);
            jdbc.update("DELETE FROM user_account WHERE id=?", id);
        }
    }
    private Map<String,String> register() throws Exception {
        String name = "v3_" + UUID.randomUUID().toString().replace("-", "").substring(0,24);
        JsonNode user = call("POST", "/auth/register", null, Map.of("username",name,"password",password,"displayName","合成客户","role","MERCHANT"),201);
        ids.add(user.get("id").asText());
        assertThat(user.get("role").asText()).isEqualTo("CUSTOMER");
        assertThat(user.toString()).doesNotContain("password", password);
        var login = call("POST", "/auth/login", null, Map.of("username", name,"password",password),200);
        return Map.of("id",user.get("id").asText(),"name",name,"token",login.get("accessToken").asText());
    }
    private JsonNode call(String method,String path,String token,Object body,int expected) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1"+path)).timeout(Duration.ofSeconds(8));
        if(token!=null) request.header("Authorization","Bearer "+token);
        request.header("Content-Type","application/json");
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        try(var client=HttpClient.newHttpClient()) {
            var response=client.send(request.build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(method+" "+path).isEqualTo(expected);
            assertThat(response.headers().firstValue("X-Request-Id")).isPresent();
            if(expected==201) assertThat(response.headers().firstValue("Location")).isPresent();
            return response.body().isEmpty()?null:json.readTree(response.body());
        }
    }
    @Test void registrationLoginMeAndDuplicateUsername() throws Exception {
        var a=register();
        assertThat(call("GET","/users/me",a.get("token"),null,200).get("id").asText()).isEqualTo(a.get("id"));
        var error=call("POST","/auth/register",null,Map.of("username",a.get("name").toUpperCase(Locale.ROOT),"password",password,"displayName","duplicate"),409);
        assertThat(error.get("code").asText()).isEqualTo("USERNAME_EXISTS");
        call("POST","/auth/register",null,Map.of("username","bad","password","short","displayName","bad"),400);
        call("POST","/auth/login",null,Map.of("username",a.get("name"),"password","wrong"),401);
        String stored=jdbc.queryForObject("SELECT password_hash FROM user_account WHERE id=?",String.class,a.get("id"));
        assertThat(stored).startsWith("$2a$").isNotEqualTo(password);
    }
    @Test void customersCanOnlyReadUpdateAndDeleteOwnSessions() throws Exception {
        var a=register();var b=register();
        var session=call("POST","/sessions",a.get("token"),Map.of("title","SYNTHETIC V3","notes","owned","userId",b.get("id")),201);
        String id=session.get("id").asText();String path="/sessions/"+id;
        assertThat(jdbc.queryForObject("SELECT user_id FROM consultation_session WHERE id=?",String.class,id)).isEqualTo(a.get("id"));
        call("GET",path,b.get("token"),null,404);
        call("PUT",path,b.get("token"),Map.of("title","attack","notes","bad","version",0),404);
        call("PATCH",path+"/status",b.get("token"),Map.of("status","CLOSED","version",0),404);
        call("DELETE",path+"?version=0",b.get("token"),null,404);
        assertThat(call("GET","/sessions",b.get("token"),null,200).get("items").size()).isZero();
        assertThat(call("GET","/sessions?keyword=SYNTHETIC&size=1",a.get("token"),null,200).get("items").size()).isEqualTo(1);
        call("PUT",path,a.get("token"),Map.of("title","updated","notes","own update","version",0),200);
        call("PUT",path,a.get("token"),Map.of("title","stale","notes","stale","version",0),409);
        call("PATCH",path+"/status",a.get("token"),Map.of("status","CLOSED","version",1),200);
        call("DELETE",path+"?version=2",a.get("token"),null,204);
        call("GET",path,a.get("token"),null,404);
    }
    @Test void unownedHistoryIsMerchantOnlyAndRoleChangesApplyToExistingToken() throws Exception {
        var a=register();String id=UUID.randomUUID().toString();unowned.add(id);
        jdbc.update("INSERT INTO consultation_session(id,title,notes) VALUES(?,?,?)",id,"SYNTHETIC LEGACY V3","unassigned");
        call("GET","/sessions/"+id,a.get("token"),null,404);
        assertThat(call("GET","/sessions?keyword=SYNTHETIC%20LEGACY%20V3",a.get("token"),null,200).get("items").size()).isZero();
        call("GET","/articles",a.get("token"),null,403);
        jdbc.update("UPDATE user_account SET role='MERCHANT',version=version+1 WHERE id=?",a.get("id"));
        call("GET","/sessions/"+id,a.get("token"),null,200);
        call("GET","/articles",a.get("token"),null,200);
        jdbc.update("UPDATE user_account SET role='CUSTOMER',version=version+1 WHERE id=?",a.get("id"));
        call("GET","/articles",a.get("token"),null,403);
    }
    @Test void disablingAccountRejectsOldTokenAndNewLogin() throws Exception {
        var a=register();
        jdbc.update("UPDATE user_account SET status='DISABLED',version=version+1 WHERE id=?",a.get("id"));
        call("GET","/users/me",a.get("token"),null,401);
        call("POST","/auth/login",null,Map.of("username",a.get("name"),"password",password),401);
    }
}
