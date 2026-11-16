package sample;

import io.github.jeongyounghyeon.alimtalk.AlimtalkTemplateClient;
import io.github.jeongyounghyeon.alimtalk.exception.AlimtalkApiException;
import io.github.jeongyounghyeon.alimtalk.model.Template;
import io.github.jeongyounghyeon.alimtalk.model.TemplateCategory;
import io.github.jeongyounghyeon.alimtalk.model.TemplateEmphasizeType;
import io.github.jeongyounghyeon.alimtalk.model.TemplateMessageType;
import io.github.jeongyounghyeon.alimtalk.model.TemplatePage;
import io.github.jeongyounghyeon.alimtalk.request.CreateTemplateRequest;
import io.github.jeongyounghyeon.alimtalk.request.TemplateSearchCondition;
import io.github.jeongyounghyeon.alimtalk.request.UpdateTemplateRequest;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 실제 NHN Cloud API 로 템플릿 관리를 확인하는 수동 실행기. {@code demo.enabled=true} 일 때만 실행된다.
 *
 * <p>검수 중에는 수정/삭제가 제한될 수 있어서, 한 번에 이어서 실행하지 않고 단계별로 나눠 실행한다.
 * <pre>
 * --demo.enabled=true --demo.action=list                              (읽기 전용)
 * --demo.enabled=true --demo.action=category                          (읽기 전용, 카테고리 코드 조회)
 * --demo.enabled=true --demo.action=create  --demo.allow-write=true
 * --demo.enabled=true --demo.action=get                               (검수 상태 확인)
 * --demo.enabled=true --demo.action=update  --demo.allow-write=true
 * --demo.enabled=true --demo.action=history
 * --demo.enabled=true --demo.action=comment --demo.allow-write=true
 * --demo.enabled=true --demo.action=delete  --demo.allow-write=true
 * </pre>
 *
 * <p>안전장치: 쓰기 작업(create/update/comment/delete)은 {@code demo.allow-write=true} 가 필요하고,
 * 템플릿 코드가 {@value #SAFE_PREFIX} 로 시작할 때만 동작한다. 운영 템플릿을 건드리지 않기 위해서다.
 */
@Component
@ConditionalOnProperty(name = "demo.enabled", havingValue = "true")
public class TemplateDemo implements CommandLineRunner {

    static final String SAFE_PREFIX = "TEST_";
    private static final Set<String> WRITE_ACTIONS = Set.of("create", "update", "comment", "delete");

    private final AlimtalkTemplateClient templates;
    private final String action;
    private final String code;
    private final boolean allowWrite;

    public TemplateDemo(
            AlimtalkTemplateClient templates,
            @Value("${demo.action:list}") String action,
            @Value("${demo.template-code:TEST_01}") String code,
            @Value("${demo.allow-write:false}") boolean allowWrite) {
        this.templates = templates;
        this.action = action;
        this.code = code;
        this.allowWrite = allowWrite;
    }

    @Override
    public void run(String... args) {
        if (WRITE_ACTIONS.contains(action)) {
            if (!allowWrite) {
                System.out.println("[중단] '" + action + "' 는 실제 템플릿을 변경합니다. --demo.allow-write=true 를 추가하세요.");
                return;
            }
            if (!code.startsWith(SAFE_PREFIX)) {
                System.out.println("[중단] 안전을 위해 템플릿 코드는 " + SAFE_PREFIX + " 로 시작해야 합니다: " + code);
                return;
            }
        }
        System.out.println("[실행] action=" + action + (action.equals("list") ? "" : ", templateCode=" + code));
        try {
            execute();
        } catch (AlimtalkApiException e) {
            System.out.println("[API 오류] resultCode=" + e.getResultCode()
                    + ", resultMessage=" + e.getResultMessage() + ", httpStatus=" + e.getHttpStatus());
        }
    }

    private void execute() {
        switch (action) {
            case "list" -> {
                TemplatePage page = templates.listTemplates(TemplateSearchCondition.builder().pageSize(10).build());
                System.out.println("총 " + page.getTotalCount() + "개");
                page.getTemplates().forEach(this::print);
            }
            case "category" -> {
                for (TemplateCategory category : templates.listTemplateCategories()) {
                    System.out.println(category.getName());
                    category.getSubCategories().forEach(sub -> System.out.println(
                            "  - " + sub.getCode() + " | " + sub.getGroupName() + " | " + sub.getName()));
                }
            }
            case "create" -> {
                templates.createTemplate(CreateTemplateRequest.builder()
                        .templateCode(code)
                        .templateName("연동 테스트")
                        .templateContent("#{이름}님, 연동 테스트 메시지입니다.")
                        .templateMessageType(TemplateMessageType.BA)
                        .templateEmphasizeType(TemplateEmphasizeType.NONE)
                        .build());
                System.out.println("등록 요청 성공. 'get' 으로 검수 상태를 확인하세요.");
            }
            case "get" -> print(templates.getTemplate(code));
            case "update" -> {
                templates.updateTemplate(code, UpdateTemplateRequest.builder()
                        .templateName("연동 테스트 (수정)")
                        .templateContent("#{이름}님, 수정된 연동 테스트 메시지입니다.")
                        .build());
                System.out.println("수정 요청 성공.");
            }
            case "history" -> {
                TemplatePage page = templates.listTemplateModifications(code);
                System.out.println("수정 이력 " + page.getTotalCount() + "건");
                page.getTemplates().forEach(this::print);
            }
            case "comment" -> {
                templates.addTemplateComment(code, "연동 테스트용 템플릿입니다. 검수 부탁드립니다.");
                System.out.println("문의 댓글 등록 성공.");
            }
            case "delete" -> {
                templates.deleteTemplate(code);
                System.out.println("삭제 요청 성공.");
            }
            default -> System.out.println("알 수 없는 action: " + action
                    + " (list|category|create|get|update|history|comment|delete)");
        }
    }

    private void print(Template t) {
        System.out.println(" - " + t.getTemplateCode()
                + " | 이름=" + t.getTemplateName()
                + " | status=" + t.getStatus() + "(" + t.getStatusName() + ")"
                + " | 수정=" + t.getUpdateDate()
                + " | 댓글=" + t.getComments().size());
    }
}
