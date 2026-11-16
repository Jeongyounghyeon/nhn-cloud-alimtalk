package sample;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jeongyounghyeon.alimtalk.AlimtalkTemplateClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Java 앱에서 starter 가 자동 설정되는지 확인한다. 네트워크 호출은 하지 않는다. */
@SpringBootTest(properties = {
        "nhn.alimtalk.app-key=test-app",
        "nhn.alimtalk.secret-key=test-secret",
        "nhn.alimtalk.default-sender-key=test-sender"
})
class SampleApplicationTests {

    @Autowired
    AlimtalkTemplateClient client;

    @Test
    void clientIsAutoConfigured() {
        assertThat(client).isNotNull();
    }
}
