package com.example.pontalweb.client;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

//PontalのAPI GET /api/employees/{id} のレスポンス(社員詳細。画面で使う項目だけ)
//JSP(EL式)は getXxx() で値を取るので、画面で使う分は getter を持たせる(record の name() では取れない)
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmployeeDetail(
        Long id,
        String name,
        String email,
        String department,
        String position,
        String joinDate,
        String gender,
        Integer age,
        String birthplace,
        String hobby,
        String bio,
        String selfQa) {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_SELF_QA = 3;

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getDepartment() { return department; }
    public String getPosition() { return position; }
    public String getGender() { return gender; }
    public String getBirthplace() { return birthplace; }
    public String getHobby() { return hobby; }
    public String getBio() { return bio; }
    public String getSelfQa() { return selfQa; }

    //名前の頭文字。顔写真の代わりに、丸いアイコンに出す
    public String getInitial() {
        return name == null || name.isEmpty() ? "" : name.substring(0, 1);
    }

    //年齢の表示("38歳")。登録されていなければ "-"
    public String getAgeText() {
        return age == null ? "-" : age + "歳";
    }

    //入社日の表示("2018年4月1日 (勤続8年)")。登録されていない・読めないときは "-"
    public String getJoinDateText() {
        if (joinDate == null || joinDate.isBlank()) {
            return "-";
        }
        try {
            LocalDate date = LocalDate.parse(joinDate); //"2018-04-01" の形
            int years = Period.between(date, LocalDate.now()).getYears();
            String text = date.getYear() + "年" + date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
            return years >= 1 ? text + " (勤続" + years + "年)" : text;
        } catch (DateTimeParseException e) {
            return joinDate; //想定外の形なら、そのまま出す
        }
    }

    //自己紹介のQ&A(selfQa が、[{"question":"...","answer":"..."}] の形のJSONのとき)
    //最大3件。JSONとして読めないとき(ふつうの文字など)や、空のときは、空のリストを返す
    public List<SelfQaItem> getSelfQaItems() {
        if (!notBlank(selfQa)) {
            return List.of();
        }
        try {
            List<SelfQaItem> items = JSON.readValue(selfQa, new TypeReference<List<SelfQaItem>>() { });
            return items.stream()
                    .filter(item -> item != null && (notBlank(item.question()) || notBlank(item.answer())))
                    .limit(MAX_SELF_QA)
                    .toList();
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    //自己紹介・Q&Aに、出せる内容があるか(全部空なら、「未登録」と出す)
    public boolean isHasProfileText() {
        return notBlank(hobby) || notBlank(bio) || notBlank(selfQa);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
