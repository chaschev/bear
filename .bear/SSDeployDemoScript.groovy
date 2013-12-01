import bear.core.IBearSettings
import bear.core.SessionContext
import bear.task.Task
import bear.task.TaskDef
import bear.task.TaskResult

class SSDeployDemoScript extends bear.main.Script {
    SessionContext _

    @Override
    TaskResult run()
    {
        _.putConst(bear.repositoryURI, 'git@github.com:chaschev/securesocial.git')
        _.putConst(bear.vcsBranchName, 'master')

        final IBearSettings settings = task.getGlobalRunner().getBearSettings()

        println "settings: " + settings.class

        def TaskDef<Task> task = settings.deployProject;

        println "task:" + task

        return _.run(task)

//        logger.info("git: {}", gitPlugin)
//        def git = gitPlugin.newSession(_, parent)
//
//        logger.info("head: {}", git.head())
//
//        final LogResult logResult = git.newPlainScript("git --no-pager log -3 --all --date-order", GitCLIPlugin.LOG_PARSER).run()
//
//        logger.info("logResult: {}", logResult.entries)
//
//        git.newPlainScript("""git --no-pager diff master~3^~2 --color""").run()
//
//        git.newPlainScript("""git branch -r --color""").run()
    }
}