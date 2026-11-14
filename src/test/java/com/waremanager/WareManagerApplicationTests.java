package com.waremanager;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")   // use the H2 test database from application-test.properties
class WareManagerApplicationTests {
    @Test
    void contextLoads() {
    }
}
