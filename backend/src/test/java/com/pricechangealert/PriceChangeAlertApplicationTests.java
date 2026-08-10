package com.pricechangealert;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:price-change-alert-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "logging.file.name="
})
class PriceChangeAlertApplicationTests {

    @Test
    void contextLoads() {
    }
}

