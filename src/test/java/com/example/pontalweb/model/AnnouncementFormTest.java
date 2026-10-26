package com.example.pontalweb.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.pontalweb.client.AnnouncementDetail;
import com.example.pontalweb.client.AnnouncementRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

//AnnouncementForm のテスト。入力チェックと、APIに送る形(宛先は、選んだ種類に合うものだけ)を確認する
class AnnouncementFormTest {

    private AnnouncementForm valid() {
        AnnouncementForm form = new AnnouncementForm();
        form.setTitle("年末調整のお願い");
        form.setBody("本文です");
        form.setCategory("対応依頼");
        return form;
    }

    // ---------- 最初の状態 ----------

    @Test
    void newForm_startsEmpty_withTargetAll() {
        AnnouncementForm form = new AnnouncementForm();

        assertThat(form.getTitle()).isEmpty();
        assertThat(form.getBody()).isEmpty();
        assertThat(form.getCategory()).isEmpty();
        assertThat(form.isImportant()).isFalse();
        assertThat(form.getTargetType()).isEqualTo("ALL");
        assertThat(form.getTargetEmployeeId()).isNull();
    }

    // ---------- 入力チェック ----------

    @Test
    void validate_returnsNoErrors_forValidInput() {
        assertThat(valid().validate()).isEmpty();
    }

    @Test
    void validate_requiresTitle() {
        AnnouncementForm form = valid();
        form.setTitle("   ");

        assertThat(form.validate()).containsOnlyKeys("title");
    }

    @Test
    void validate_titleMax100() {
        AnnouncementForm form = valid();
        form.setTitle("あ".repeat(100));
        assertThat(form.validate()).isEmpty();

        form.setTitle("あ".repeat(101));
        assertThat(form.validate()).containsOnlyKeys("title");
    }

    @Test
    void validate_requiresBody_andMax2000() {
        AnnouncementForm form = valid();
        form.setBody("");
        assertThat(form.validate()).containsOnlyKeys("body");

        form.setBody("あ".repeat(2000));
        assertThat(form.validate()).isEmpty();

        form.setBody("あ".repeat(2001));
        assertThat(form.validate()).containsOnlyKeys("body");
    }

    @Test
    void validate_categoryMustBeOneOfTheChoices() {
        AnnouncementForm form = valid();
        form.setCategory("");
        assertThat(form.validate()).containsOnlyKeys("category");

        form.setCategory("雑談");
        assertThat(form.validate()).containsOnlyKeys("category");

        for (String category : AnnouncementForm.CATEGORIES) {
            form.setCategory(category);
            assertThat(form.validate()).isEmpty();
        }
    }

    @Test
    void validate_departmentTarget_requiresDepartment() {
        AnnouncementForm form = valid();
        form.setTargetType("DEPARTMENT");
        assertThat(form.validate()).containsOnlyKeys("target");

        form.setTargetDepartment("開発部");
        assertThat(form.validate()).isEmpty();
    }

    @Test
    void validate_employeeTarget_requiresEmployee() {
        AnnouncementForm form = valid();
        form.setTargetType("EMPLOYEE");
        assertThat(form.validate()).containsOnlyKeys("target");

        form.setTargetEmployeeId(3L);
        assertThat(form.validate()).isEmpty();
    }

    @Test
    void validate_unknownTargetType_isError() {
        AnnouncementForm form = valid();
        form.setTargetType("EVERYONE");

        assertThat(form.validate()).containsOnlyKeys("target");
    }

    @Test
    void validate_collectsAllErrorsAtOnce_inScreenOrder() {
        AnnouncementForm form = new AnnouncementForm();
        form.setTargetType("DEPARTMENT");

        Map<String, String> errors = form.validate();

        //全部の項目のまちがいを、いっぺんに返す(1つずつ、直させない)。画面の上から下の順
        assertThat(errors.keySet()).containsExactly("title", "category", "target", "body");
    }

    // ---------- APIに送る形(宛先は、選んだ種類に合うものだけ) ----------

    @Test
    void toRequest_targetAll_sendsNoDepartmentAndNoEmployee() {
        AnnouncementForm form = valid();
        //別の種類を選んでいたときの値が残っていても、全員向けなら、送らない
        form.setTargetDepartment("開発部");
        form.setTargetEmployeeId(3L);

        AnnouncementRequest request = form.toRequest();

        assertThat(request.targetType()).isEqualTo("ALL");
        assertThat(request.targetDepartment()).isNull();
        assertThat(request.targetEmployeeId()).isNull();
    }

    @Test
    void toRequest_targetDepartment_sendsOnlyDepartment() {
        AnnouncementForm form = valid();
        form.setTargetType("DEPARTMENT");
        form.setTargetDepartment("開発部");
        form.setTargetEmployeeId(3L);

        AnnouncementRequest request = form.toRequest();

        assertThat(request.targetDepartment()).isEqualTo("開発部");
        assertThat(request.targetEmployeeId()).isNull();
    }

    @Test
    void toRequest_targetEmployee_sendsOnlyEmployee() {
        AnnouncementForm form = valid();
        form.setTargetType("EMPLOYEE");
        form.setTargetDepartment("開発部");
        form.setTargetEmployeeId(3L);

        AnnouncementRequest request = form.toRequest();

        assertThat(request.targetDepartment()).isNull();
        assertThat(request.targetEmployeeId()).isEqualTo(3L);
    }

    @Test
    void toRequest_copiesTitleBodyCategoryAndImportant() {
        AnnouncementForm form = valid();
        form.setImportant(true);

        AnnouncementRequest request = form.toRequest();

        assertThat(request.title()).isEqualTo("年末調整のお願い");
        assertThat(request.body()).isEqualTo("本文です");
        assertThat(request.category()).isEqualTo("対応依頼");
        assertThat(request.important()).isTrue();
    }

    @Test
    void toRequest_isSentAsJson_withApiFieldNames() throws Exception {
        AnnouncementForm form = valid();
        form.setImportant(true);
        form.setTargetType("EMPLOYEE");
        form.setTargetEmployeeId(3L);

        String json = new ObjectMapper().writeValueAsString(form.toRequest());

        //APIが受け取る名前(isImportant)で送られる
        assertThat(json).contains("\"isImportant\":true");
        assertThat(json).contains("\"targetType\":\"EMPLOYEE\"").contains("\"targetEmployeeId\":3");
        assertThat(json).contains("\"targetDepartment\":null");
        assertThat(json).doesNotContain("\"important\"");
    }

    // ---------- 編集: いまの内容をフォームに入れる ----------

    private AnnouncementDetail detail(String targetType, String targetDepartment, Long targetEmployeeId) {
        return new AnnouncementDetail(7L, "タイトル", "技術", true, "総務部", targetType, "ラベル",
                "2026-10-22T10:30:00", "本文", "2026-10-22T10:30:00", true, false, targetDepartment, targetEmployeeId);
    }

    @Test
    void from_copiesDetailIntoForm() {
        AnnouncementForm form = AnnouncementForm.from(detail("DEPARTMENT", "開発部", null));

        assertThat(form.getTitle()).isEqualTo("タイトル");
        assertThat(form.getBody()).isEqualTo("本文");
        assertThat(form.getCategory()).isEqualTo("技術");
        assertThat(form.isImportant()).isTrue();
        assertThat(form.getTargetType()).isEqualTo("DEPARTMENT");
        assertThat(form.getTargetDepartment()).isEqualTo("開発部");
    }

    @Test
    void from_employeeTarget_keepsEmployeeId_andEmptyDepartment() {
        AnnouncementForm form = AnnouncementForm.from(detail("EMPLOYEE", null, 3L));

        assertThat(form.getTargetEmployeeId()).isEqualTo(3L);
        assertThat(form.getTargetDepartment()).isEmpty(); //nullではなく、空の文字(画面の比較で、扱いやすいため)
    }

    @Test
    void from_thenValidate_hasNoErrors() {
        assertThat(AnnouncementForm.from(detail("ALL", null, null)).validate()).isEmpty();
    }
}
