package com.example.pontalweb.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//アプリが使う「現在の時刻」(Clock)。日本時間に固定する
//サーバーのタイムゾーン(DockerやAWSでは、UTCのことが多い)に左右されず、日本の時刻で、あいさつなどを決めるため
//また、テストでは、決まった時刻のClockに差し替えられる
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Tokyo"));
    }
}
