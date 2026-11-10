// CI（GitHub Actions 等）直接走官方源：国内镜像对海外网络不稳定（502 会把模块钉死在镜像上导致解析失败）
// 注意：pluginManagement 在主脚本体之前求值，块内不能引用顶层 val，只能各自内联判断
pluginManagement {
    repositories {
        if (System.getenv("CI") == null) {
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getenv("CI") == null) {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "Timetable"
include(":app")
