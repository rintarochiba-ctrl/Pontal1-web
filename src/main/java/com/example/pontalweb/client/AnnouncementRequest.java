package com.example.pontalweb.client;

import com.fasterxml.jackson.annotation.JsonProperty;

//PontalのAPI POST /api/announcements、PUT /api/announcements/{id} に送る、お知らせの内容(JSONになってAPIに送られる)
//APIのJSONの項目名は isImportant なので、@JsonProperty で、その名前に合わせる(送るときも、この名前で送られる)
//宛先は、targetTypeに合う項目だけを入れる(ALL=どちらもnull / DEPARTMENT=targetDepartmentだけ / EMPLOYEE=targetEmployeeIdだけ)
public record AnnouncementRequest(
        String title,
        String body,
        String category,
        @JsonProperty("isImportant") boolean important,
        String targetType,
        String targetDepartment,
        Long targetEmployeeId) {
}
