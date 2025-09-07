package net.lumalyte.lg.application.services

import net.lumalyte.lg.config.MainConfig

interface ConfigService {
    fun loadConfig(): MainConfig
}
