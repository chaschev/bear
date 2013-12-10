import bear.core.SessionContext
import bear.main.Grid
import bear.plugins.db.DbDumpInfo
import bear.plugins.db.DbDumpManager
import bear.plugins.db.DumpManagerPlugin
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
            final DbDumpManager.DbService dumpService = _.var(dumpManager.dbService)

            final DbDumpInfo dump = dumpService.createDump("ss_demo")

            final List<DbDumpInfo> dumps = dumpService.listDumps()

            println "created dump $dump, rolling back to ${dumps[0]}"

            dumpService.restoreDump(dumps[0])

            println dumpService.printDumpInfo(dumpService.listDumps())

            return TaskResult.OK
        } as TaskCallable<TaskDef>);
    }
}
