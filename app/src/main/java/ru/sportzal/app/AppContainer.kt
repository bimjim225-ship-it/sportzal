package ru.sportzal.app
import android.content.Context
import ru.sportzal.app.data.db.SportzalDatabase
import ru.sportzal.app.data.files.ProgramValidator
import ru.sportzal.app.data.files.ProgramImporter
import ru.sportzal.app.domain.WorkoutService
import ru.sportzal.app.data.repository.*
class AppContainer(context:Context){val database=SportzalDatabase.create(context);val repository:SportzalRepository=RoomSportzalRepository(database);val programValidator=ProgramValidator();val programImporter=ProgramImporter(context.contentResolver,programValidator,repository);val workoutService=WorkoutService(repository)}
