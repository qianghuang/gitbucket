package service

import model._
import scala.slick.driver.H2Driver.simple._
import Database.threadLocalSession
import util.JGitUtil
import javax.servlet.ServletContext

trait ProjectService { self: AccountService =>
  import ProjectService._

  /**
   * Creates a new project.
   *
   * The project is created as public repository at first. Users can modify the project type at the repository settings
   * page after the project creation to configure the project as the private repository.
   *
   * @param projectName the project name
   * @param userName the user name of the project owner
   * @param description the project description
   * @return the created project id
   */
  def createProject(projectName: String, userName: String, description: Option[String]): Long = {
    // TODO create a git repository also here?

    val currentDate = new java.sql.Date(System.currentTimeMillis)

    Projects.ins returning Projects.projectId insert
      Project(
        projectId        = None,
        projectName      = projectName,
        userId           = getUserId(userName),
        projectType      = Public,
        description      = description,
        defaultBranch    = "master",
        registeredDate   = currentDate,
        updatedDate      = currentDate,
        lastActivityDate = currentDate)
  }

  /**
   * Returns the specified user's project list.
   *
   * @param userName the user name
   * @return the project list which is sorted in descending order of lastActivityDate.
   */
  def getRepositoriesOfUser(userName: String, servletContext: ServletContext): List[RepositoryInfo] = {
    (Query(Projects) filter(_.userId is getUserId(userName).bind) sortBy(_.lastActivityDate desc) list) map { project =>
      val repositoryInfo = JGitUtil.getRepositoryInfo(userName, project.projectName, servletContext)
      RepositoryInfo(repositoryInfo.owner, repositoryInfo.name, repositoryInfo.url, project, repositoryInfo.branchList, repositoryInfo.tags)
    }
  }

  def getRepository(userName: String, projectName: String, servletContext: ServletContext): Option[RepositoryInfo] = {
    (Query(Projects) filter { project =>
      (project.userId is getUserId(userName).bind) && (project.projectName is projectName.bind)
    } firstOption) map { project =>
      val repositoryInfo = JGitUtil.getRepositoryInfo(userName, project.projectName, servletContext)
      RepositoryInfo(repositoryInfo.owner, repositoryInfo.name, repositoryInfo.url, project, repositoryInfo.branchList, repositoryInfo.tags)
    }
  }

  def getAccessibleRepositories(account: Option[Account], servletContext: ServletContext): List[RepositoryInfo] = {
    account match {
      case Some(x) => {
        (Query(Projects) sortBy(_.lastActivityDate desc) list) map { project =>
          // TODO ユーザ名はジョインして取得する？
          val repositoryInfo = JGitUtil.getRepositoryInfo(getUserName(project.userId), project.projectName, servletContext)
          RepositoryInfo(repositoryInfo.owner, repositoryInfo.name, repositoryInfo.url, project, repositoryInfo.branchList, repositoryInfo.tags)
        }
      }
      case None => {
        (Query(Projects) filter(_.projectType is Public.bind) sortBy(_.lastActivityDate desc) list) map { project =>
          // TODO ユーザ名はジョインして取得する？
          val repositoryInfo = JGitUtil.getRepositoryInfo(getUserName(project.userId), project.projectName, servletContext)
          RepositoryInfo(repositoryInfo.owner, repositoryInfo.name, repositoryInfo.url, project, repositoryInfo.branchList, repositoryInfo.tags)
        }
      }
    }
  }

  private def getUserName(userId: Long): String = getAccountByUserId(userId).get.userName

  private def getUserId(userName: String): Long = getAccountByUserName(userName).get.userId.get

}

object ProjectService {

  val Public = 0
  val Private = 1

  case class RepositoryInfo(owner: String, name: String, url: String, project: Project, branchList: List[String], tags: List[util.JGitUtil.TagInfo])
}