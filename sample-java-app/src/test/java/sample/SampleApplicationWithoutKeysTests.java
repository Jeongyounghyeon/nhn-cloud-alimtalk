package sample;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jeongyounghyeon.alimtalk.AlimtalkTemplateClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 키가 비어 있는 환경(로컬, 테스트, 스테이지)에서도 앱이 기동하고, 클라이언트는 선택적으로 주입받을 수 있는지 확인한다.
 * 환경 변수가 없거나 `app-key: ${MY_KEY:}` 처럼 빈 값이 되는 설정과 같은 상황이다.
 */
@SpringBootTest(properties = {"nhn.alimtalk.app-key=", "nhn.alimtalk.secret-key="})
class SampleApplicationWithoutKeysTests {

    @Autowired
    ObjectProvider<AlimtalkTemplateClient> client;

    @Test
    void appStartsWithoutClientWhenKeysAreEmpty() {
        assertThat(client.getIfAvailable()).isNull();
    }
}
