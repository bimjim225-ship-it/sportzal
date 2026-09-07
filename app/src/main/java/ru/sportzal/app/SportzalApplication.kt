package ru.sportzal.app
import android.app.Application
class SportzalApplication:Application(){lateinit var container:AppContainer;private set;override fun onCreate(){super.onCreate();container=AppContainer(this)}}
