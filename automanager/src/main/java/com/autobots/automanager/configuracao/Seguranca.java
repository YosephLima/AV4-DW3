package com.autobots.automanager.configuracao;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.autobots.automanager.adaptadores.UserDetailsServiceImpl;
import com.autobots.automanager.filtros.Autenticador;
import com.autobots.automanager.filtros.Autorizador;
import com.autobots.automanager.jwt.ProvedorJwt;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Toda rota exige um JWT válido, exceto o POST /login (atendido pelo filtro Autenticador antes
 * desta checagem) e a página de erro. Quais perfis podem usar cada rota fica nos @PreAuthorize
 * dos controles, com as regras da Tabela 1 em autorizacao/PoliticaAcesso.
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class Seguranca extends WebSecurityConfigurerAdapter {
	@Autowired
	private UserDetailsServiceImpl servico;
	@Autowired
	private ProvedorJwt provedorJwt;
	@Autowired
	private PasswordEncoder codificador;
	@Autowired
	private ObjectMapper mapeador;

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		RespostasSeguranca respostas = new RespostasSeguranca();
		http.cors().and().csrf().disable()
				.httpBasic().disable()
				.formLogin().disable()
				.logout().disable();
		http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
		http.exceptionHandling().authenticationEntryPoint(respostas).accessDeniedHandler(respostas);
		// As mensagens de erro dos controles (sendError) são montadas na rota /error
		http.authorizeRequests().antMatchers("/error").permitAll().anyRequest().authenticated();

		http.addFilter(new Autenticador(authenticationManager(), provedorJwt, servico, mapeador));
		http.addFilter(new Autorizador(authenticationManager(), provedorJwt, servico));
	}

	@Override
	protected void configure(AuthenticationManagerBuilder autenticador) throws Exception {
		autenticador.userDetailsService(servico).passwordEncoder(codificador);
	}

	// Libera chamadas de outras origens (um front-end, por exemplo) e deixa o navegador ler o
	// cabeçalho Authorization devolvido no login
	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuracao = new CorsConfiguration().applyPermitDefaultValues();
		configuracao.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		configuracao.addExposedHeader(HttpHeaders.AUTHORIZATION);
		configuracao.addExposedHeader(HttpHeaders.LOCATION);
		UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
		fonte.registerCorsConfiguration("/**", configuracao);
		return fonte;
	}
}
