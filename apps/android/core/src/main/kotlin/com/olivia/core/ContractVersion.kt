package com.olivia.core

/**
 * Versão dos contratos entre app, IA, mock e hardware real.
 *
 * Semver: MAJOR quebra consumidores, MINOR adiciona de forma compatível, PATCH é
 * correção de documentação ou detalhe interno. Todo evento serializado carrega
 * este valor, para detectar incompatibilidade sem interpretar dados antigos como
 * atuais. Migração de versões persistidas deve ser explícita e preservar o original.
 */
public object ContractVersion {
    public const val CURRENT: String = "2.0.0"

    /** Leitura conservadora: não interpretar schema desconhecido como versão atual. */
    public fun requireCurrent(version: String) {
        require(version == CURRENT) { "Versão de contrato não suportada" }
    }
}
