package com.autobots.automanager.filtros;

class AnalisadorCabecalho {
	private String cabecalho;

	public AnalisadorCabecalho(String cabecalho) {
		this.cabecalho = cabecalho;
	}

	// Quem chama já conferiu o prefixo "Bearer " com o ValidadorCabecalho
	public String obterJwt() {
		return cabecalho.substring(7).trim();
	}
}
