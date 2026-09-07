package ru.sportzal.app
import android.content.Context
import ru.sportzal.app.data.db.SportzalDatabase
import ru.sportzal.app.data.files.ProgramValidator
import ru.sportzal.app.data.repository.*
class AppContainer(context:Context){val database=SportzalDatabase.create(context);val repository:SportzalRepository=RoomSportzalRepository(database);val programValidator=ProgramValidator()}
