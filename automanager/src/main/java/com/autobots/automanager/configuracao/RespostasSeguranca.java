package com.autobots.automanager.configuracao;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Respostas 401 e 403. O sendError reaproveita o corpo de erro padrão do Spring Boot, o mesmo
 * das demais respostas de erro da API (status, error, message e path).
 */
public class RespostasSeguranca implements AuthenticationEntryPoint, AccessDeniedHandler {

	// Sem token, token inválido ou expirado, ou credencial inativada depois do login
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException excecao) throws IOException {
		response.sendError(HttpStatus.UNAUTHORIZED.value(),
				"Autenticação necessária: envie o cabeçalho Authorization: Bearer <token>, obtido em POST /login");
	}

	// Autenticado, mas o perfil não permite a operação sobre este recurso
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException excecao) throws IOException {
		response.sendError(HttpStatus.FORBIDDEN.value(),
				"Acesso negado: o perfil do usuário autenticado não permite esta operação");
	}
}
