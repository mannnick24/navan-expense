pluginManagement {
    plugins {
        id("org.springframework.boot") version "4.1.1"
    }
}

rootProject.name = "navan-expense"

include("receipt-parser", "api")
