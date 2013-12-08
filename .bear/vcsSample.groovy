import bear.core.SessionContext
import bear.task.TaskResult
import bear.vcs.GitCLIPlugin
import bear.vcs.VcsLogInfo

class VcsSampleScript extends bear.main.Script {
    GitCLIPlugin gitPlugin;

    SessionContext _

    @Override
    TaskResult run()
    {
        _.putConst(bear.repositoryURI, 'git@github.com:chaschev/grailstwitter.git')
        _.putConst(bear.vcsBranchName, 'master')

        logger.info("git: {}", gitPlugin)

        def git = gitPlugin.newSession(_, parent)

        logger.info("head: {}", git.head())

        final VcsLogInfo logResult = git.newPlainScript("git --no-pager log -3 --all --date-order", GitCLIPlugin.LOG_PARSER).run()

        logger.info("logResult: {}", logResult.entries)

        git.newPlainScript("""git --no-pager diff master~3^~2 --color""").run()

        git.newPlainScript("""git branch -r --color""").run()
    }
}