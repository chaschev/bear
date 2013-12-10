import bear.core.SessionContext
import bear.main.Grid
import bear.plugins.db.DbDumpInfo
import bear.plugins.db.DumpManagerPlugin
import bear.plugins.db.MongoDbService
import bear.task.Task
import bear.task.TaskCallable
import bear.task.TaskDef
import bear.task.TaskResult
//@CompileStatic
class SSDumpSample extends Grid {
    DumpManagerPlugin dumpManager;

    @Override
    public void addPhases(){
        b.add({ SessionContext _, Task task, Object input ->
            final MongoDbService dumpService = _.var(dumpManager.dbService) as MongoDbService

            final DbDumpInfo dump = dumpService.createDump("ss_demo")

            dumpService.restoreDump(dumpService.listDumps().find{it.name == "20131209.233534.GMT"})

            println dumpService.printDumpInfo(dumpService.listDumps())

            return TaskResult.OK
        } as TaskCallable<TaskDef>);
    }
}
