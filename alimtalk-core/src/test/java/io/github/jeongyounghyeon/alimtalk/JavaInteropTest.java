package io.github.jeongyounghyeon.alimtalk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.jeongyounghyeon.alimtalk.exception.AlimtalkApiException;
import io.github.jeongyounghyeon.alimtalk.exception.AlimtalkException;
import io.github.jeongyounghyeon.alimtalk.internal.JdkHttpAlimtalkTemplateClient;
import io.github.jeongyounghyeon.alimtalk.model.TemplateButton;
import io.github.jeongyounghyeon.alimtalk.model.TemplateCategory;
import io.github.jeongyounghyeon.alimtalk.model.TemplateSubCategory;
import io.github.jeongyounghyeon.alimtalk.model.TemplateEmphasizeType;
import io.github.jeongyounghyeon.alimtalk.model.TemplateMessageType;
import io.github.jeongyounghyeon.alimtalk.model.TemplateStatus;
import io.github.jeongyounghyeon.alimtalk.request.CreateTemplateRequest;
import io.github.jeongyounghyeon.alimtalk.request.TemplateSearchCondition;
import io.github.jeongyounghyeon.alimtalk.request.UpdateTemplateRequest;
import java.net.http.HttpClient;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Java 소비자 관점에서 public API 가 자연스럽게 쓰이는지 확인한다. 컴파일되는 것 자체가 검증이다. */
class JavaInteropTest {

    @Test
    void builderChainingWorksFromJava() {
        CreateTemplateRequest request = CreateTemplateRequest.builder()
                .templateCode("ORDER_01")
                .templateName("주문 완료")
                .templateContent("#{이름}님 주문이 완료되었습니다.")
                .templateMessageType(TemplateMessageType.BA)
                .templateEmphasizeType(TemplateEmphasizeType.NONE)
                .buttons(List.of(TemplateButton.web(1, "주문 조회", "https://example.com")))
                .build();

        assertThat(request.getTemplateCode()).isEqualTo("ORDER_01");
        assertThat(request.getBody().getTemplateName()).isEqualTo("주문 완료");

        UpdateTemplateRequest update = UpdateTemplateRequest.builder()
                .templateName("n")
                .templateContent("c")
                .build();
        assertThat(update.getBody().getTemplateContent()).isEqualTo("c");
    }

    @Test
    void searchConditionAndConfigOverloads() {
        TemplateSearchCondition condition = TemplateSearchCondition.builder()
                .templateStatus(TemplateStatus.APPROVED)
                .pageSize(10)
                .build();
        assertThat(condition.getPageSize()).isEqualTo(10);
        assertThat(TemplateSearchCondition.all().getPageNum()).isNull();

        // @JvmOverloads 로 기본 인자 생략 가능
        AlimtalkClientConfig config = new AlimtalkClientConfig("appkey", "secret");
        assertThat(config.getBaseUrl()).isEqualTo(AlimtalkClientConfig.DEFAULT_BASE_URL);
    }

    /**
     * 이 메서드에는 throws 선언이 없다. 컴파일된다는 것 자체가 예외가 unchecked 라는 증거이고,
     * 아래 단언은 Java 에서 부모 타입 하나로 잡고 getter 로 사유를 읽을 수 있는지 확인한다.
     */
    @Test
    void apiFailureIsUncheckedAndReadableFromJava() {
        AlimtalkTemplateClient client = mock(AlimtalkTemplateClient.class);
        doThrow(new AlimtalkApiException(-1006, "Template not found", 200)).when(client).deleteTemplate("X");

        assertThatThrownBy(() -> client.deleteTemplate("X"))
                .isInstanceOf(RuntimeException.class)
                .isInstanceOf(AlimtalkException.class)
                .isInstanceOfSatisfying(AlimtalkApiException.class, e -> {
                    assertThat(e.getResultCode()).isEqualTo(-1006);
                    assertThat(e.getResultMessage()).isEqualTo("Template not found");
                    assertThat(e.getHttpStatus()).isEqualTo(200);
                });
    }

    @Test
    void clientCanBeConstructedFromJavaWithOptionalCustomizer() {
        AlimtalkClientConfig config = new AlimtalkClientConfig("appkey", "secret");

        // @JvmOverloads 로 customizer 생략 가능, 람다(Consumer)로 HttpClient.Builder 조정 가능
        AlimtalkTemplateClient plain = new JdkHttpAlimtalkTemplateClient(config);
        AlimtalkTemplateClient customized =
                new JdkHttpAlimtalkTemplateClient(config, builder -> builder.version(HttpClient.Version.HTTP_1_1));

        assertThat(plain).isNotSameAs(customized);
    }

    @Test
    void categoriesAreReadableFromJava() {
        AlimtalkTemplateClient client = mock(AlimtalkTemplateClient.class);
        when(client.listTemplateCategories()).thenReturn(List.of(
                new TemplateCategory("서비스이용", List.of(new TemplateSubCategory("001001", "이용안내", "회원", null, null)))));

        TemplateCategory category = client.listTemplateCategories().get(0);
        TemplateSubCategory sub = category.getSubCategories().get(0);

        assertThat(category.getName()).isEqualTo("서비스이용");
        assertThat(sub.getCode()).isEqualTo("001001");
        // 조회한 코드를 그대로 등록 요청에 사용
        CreateTemplateRequest request = CreateTemplateRequest.builder()
                .templateCode("C1").templateName("n").templateContent("c").categoryCode(sub.getCode()).build();
        assertThat(request.getBody().getCategoryCode()).isEqualTo("001001");
    }
}
