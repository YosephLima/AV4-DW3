package com.autobots.automanager.autorizacao;

/**
 * Expressões de @PreAuthorize por perfil. As regras que dependem do registro (de quem é, quais
 * perfis o alvo tem) ficam na PoliticaAcesso, chamada como @acesso nas expressões.
 */
public final class Perfis {
	public static final String ADMINISTRADOR = "hasRole('ADMINISTRADOR')";
	public static final String ADMINISTRADOR_GERENTE = "hasAnyRole('ADMINISTRADOR','GERENTE')";
	public static final String FUNCIONARIOS = "hasAnyRole('ADMINISTRADOR','GERENTE','VENDEDOR')";
	public static final String AUTENTICADO = "isAuthenticated()";

	private Perfis() {
	}
}
