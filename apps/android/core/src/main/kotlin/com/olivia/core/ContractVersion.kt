package com.olivia.core

/**
 * Versão dos contratos entre app, IA, mock e hardware real.
 *
 * Semver: MAJOR quebra consumidores, MINOR adiciona de forma compatível, PATCH é
 * correção de documentação ou detalhe interno. Todo evento serializado carrega
 * este valor, para que uma timeline gravada antes de uma mudança continue legível.
 */
public object ContractVersion {
    public const val CURRENT: String = "1.1.0"
}
