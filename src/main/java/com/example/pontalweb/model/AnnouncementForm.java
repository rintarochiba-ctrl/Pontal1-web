package com.example.pontalweb.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.pontalweb.client.AnnouncementDetail;
import com.example.pontalweb.client.AnnouncementRequest;

//お知らせの作成・編集フォームの入力内容。フォームの値が、ここに入る(Springが、setterで入れる)
//  ・画面(JSP)は、${form.title} のように、ここから値を取り出して、入力欄に出す(入力まちがいで戻したときも、入力した文字が残る)
//  ・簡単な入力チェック(必須と文字数)は、ここで行う。細かいチェックは、API側が行う
public class AnnouncementForm {

    //種類の選択肢(APIが受け付ける値と、同じ)
    public static final List<String> CATEGORIES = List.of("技術", "社内情報", "対応依頼");
    public static final int TITLE_MAX = 100;
    public static final int BODY_MAX = 2000;

    private String title = "";
    private String body = "";
    private String category = "";
    private boolean important;
    //宛先の種類(ALL=全員 / DEPARTMENT=部署 / EMPLOYEE=個人)。最初は、全員
    private String targetType = "ALL";
    private String targetDepartment = "";
    private Long targetEmployeeId;

    //編集画面で、いまの内容をフォームに入れる
    public static AnnouncementForm from(AnnouncementDetail detail) {
        AnnouncementForm form = new AnnouncementForm();
        form.title = detail.getTitle();
        form.body = detail.getBody();
        form.category = detail.getCategory();
        form.important = detail.isImportant();
        form.targetType = detail.getTargetType();
        form.targetDepartment = detail.getTargetDepartment() == null ? "" : detail.getTargetDepartment();
        form.targetEmployeeId = detail.getTargetEmployeeId();
        return form;
    }

    //入力チェック。まちがいがあれば、「項目の名前 → メッセージ」で返す(まちがいが無ければ、空)
    //項目の名前は、title / body / category / target(宛先)
    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (title == null || title.isBlank()) {
            errors.put("title", "タイトルを入力してください");
        } else if (title.length() > TITLE_MAX) {
            errors.put("title", "タイトルは" + TITLE_MAX + "文字以内で入力してください");
        }
        if (category == null || !CATEGORIES.contains(category)) {
            errors.put("category", "種類を選んでください");
        }
        if ("DEPARTMENT".equals(targetType) && (targetDepartment == null || targetDepartment.isBlank())) {
            errors.put("target", "宛先の部署を選んでください");
        } else if ("EMPLOYEE".equals(targetType) && targetEmployeeId == null) {
            errors.put("target", "宛先の社員を選んでください");
        } else if (!"ALL".equals(targetType) && !"DEPARTMENT".equals(targetType) && !"EMPLOYEE".equals(targetType)) {
            errors.put("target", "宛先を選んでください");
        }
        if (body == null || body.isBlank()) {
            errors.put("body", "本文を入力してください");
        } else if (body.length() > BODY_MAX) {
            errors.put("body", "本文は" + BODY_MAX + "文字以内で入力してください");
        }
        return errors;
    }

    //APIに送る形にする。宛先は、選んだ種類に合うものだけを入れる(APIは、使わない側の宛先が入っていると、400を返すため)
    public AnnouncementRequest toRequest() {
        //部署宛て(DEPARTMENT)のときだけ部署名、それ以外は null
        String department = "DEPARTMENT".equals(targetType) ? targetDepartment : null;
        //個人宛て(EMPLOYEE)のときだけ社員の番号、それ以外は null
        Long employeeId = "EMPLOYEE".equals(targetType) ? targetEmployeeId : null;
        return new AnnouncementRequest(title, body, category, important, targetType, department, employeeId);
    }

    //JSPのEL(${form.title} など)は getter、フォームの値を入れるときは setter が呼ばれる
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public boolean isImportant() { return important; }
    public void setImportant(boolean important) { this.important = important; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetDepartment() { return targetDepartment; }
    public void setTargetDepartment(String targetDepartment) { this.targetDepartment = targetDepartment; }

    public Long getTargetEmployeeId() { return targetEmployeeId; }
    public void setTargetEmployeeId(Long targetEmployeeId) { this.targetEmployeeId = targetEmployeeId; }
}
