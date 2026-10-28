package com.example.pontalweb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.example.pontalweb.security.CognitoAuthenticationProvider;
import com.example.pontalweb.security.LoginFailureHandler;
import com.example.pontalweb.security.LoginSuccessHandler;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

//画面の認証・認可のルール
//  ・ログイン画面とCSS以外は、ログインが必要(未ログインなら、ログイン画面に移動する)
//  ・給与管理はHR管理者だけ、監査ログはシステム管理者だけ
//  ・CSRF対策は、標準でオン(フォームに、確認用のトークンが要る)
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            CognitoAuthenticationProvider authenticationProvider,
            LoginSuccessHandler successHandler,
            LoginFailureHandler failureHandler) throws Exception {
        http
                //ログインの確認は、CognitoAuthenticationProviderに任せる
                //窓口(AuthenticationManager)を、明示的に1つだけ指定する
                //(providerをBeanにしているので、指定しないと、Spring Securityが、もう1か所にも自動で登録して、
                // 1回のログインで、Cognitoが2回呼ばれてしまう。失敗が続くと、Cognito側でロックされやすくなる)
                .authenticationManager(new ProviderManager(authenticationProvider))
                .authorizeHttpRequests(auth -> auth
                        //内部の転送(Controllerから、JSPへの転送など)と、エラー画面への転送は、確認しない
                        //(これが無いと、JSPへの転送が、「ログインしていないアクセス」と判定されて、ログイン画面から出られなくなる)
                        .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
                        //ログインしなくても見られるもの。/error は、エラー画面を出すための内部の入り口
                        //new-password は、初回ログインの途中の画面(まだログイン前)
                        .requestMatchers("/login", "/new-password", "/css/**", "/error").permitAll()
                        //権限が必要なもの(権限が足りなければ、403の画面になる)
                        .requestMatchers("/payroll/**").hasRole("HR_ADMIN")
                        .requestMatchers("/audit-logs/**").hasRole("SYSTEM_ADMIN")
                        .requestMatchers("/employees/new").hasRole("SYSTEM_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/employees").hasRole("SYSTEM_ADMIN")
                        //それ以外は、ログインしていれば、誰でも見られる
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login") //ログインフォームの送信先(POST)。処理は、Spring Securityが行う
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler))
                .logout(logout -> logout
                        .logoutUrl("/logout") //POSTで呼ぶ(CSRFのトークンつき)
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true) //セッションを捨てる(トークンも、一緒に消える)
                        .deleteCookies("JSESSIONID"))
                .exceptionHandling(ex -> ex
                        //ログインはしているが、権限が足りない → 403のエラー画面(/error 経由)
                        .accessDeniedHandler((request, response, e) -> response.sendError(HttpServletResponse.SC_FORBIDDEN)));

        return http.build();
    }
}
