package com.autobots.automanager.configuracao;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// Fica fora da classe Seguranca para o seed e os controles usarem o codificador sem ciclo de dependências
@Configuration
public class ConfiguracaoSenha {
	@Bean
	public PasswordEncoder codificadorSenha() {
		return new BCryptPasswordEncoder();
	}
}
