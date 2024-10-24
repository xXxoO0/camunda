//go:build ( linux || darwin )

package unix

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
        "runtime"
        "syscall"
)


func (w *UnixC8Run) OpenBrowser(name string) {
	operateUrl := "http://localhost:8080/operate/login"
        var openBrowserCmdString string
        if runtime.GOOS == "darwin" {
	        openBrowserCmdString = "open " + operateUrl
        } else if runtime.GOOS == "linux" {
	        openBrowserCmdString = "xdg-open " + operateUrl
        } else {
                panic("platform " + runtime.GOOS + "is not supported")
        }
	openBrowserCmd := exec.Command(openBrowserCmdString)
	fmt.Println(name + " has successfully been started.")
	openBrowserCmd.Run()
}

func (w *UnixC8Run) GetProcessTree(commandPid int) []*os.Process{
        // For unix systems we can kill all processes within a process group by setting a pgid
        // therefore, only the main process needs put in here.
        process := os.Process{Pid: commandPid}
        processes := []*os.Process{&process}
        return processes
}

func (w *UnixC8Run) GetVersionCmd(javaBinaryPath string) *exec.Cmd {
        return exec.Command(javaBinaryPath + " --version")
}

func (w *UnixC8Run) GetElasticsearchCmd(elasticsearchVersion string, parentDir string) *exec.Cmd {
        elasticsearchCmdString := filepath.Join(parentDir, "elasticsearch-"+elasticsearchVersion, "bin", "elasticsearch") + " -E xpack.ml.enabled=false -E xpack.security.enabled=false"
        elasticsearchCmd := exec.Command(elasticsearchCmdString)
        elasticsearchCmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
        return elasticsearchCmd
}

func (w *UnixC8Run) GetConnectorsCmd(javaBinary string, parentDir string, camundaVersion string) *exec.Cmd {
        connectorsCmdString := javaBinary + " -classpath " + parentDir + "/*:" + parentDir + "/custom_connectors/*:" + parentDir + "/camunda-zeebe-" + camundaVersion + "/lib/* io.camunda.connector.runtime.app.ConnectorRuntimeApplication --spring.config.location=" + parentDir + "/connectors-application.properties"
        connectorsCmd := exec.Command(connectorsCmdString)
        connectorsCmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
        return connectorsCmd
}

func (w *UnixC8Run) GetCamundaCmd(camundaVersion string, parentDir string, extraArgs string) *exec.Cmd {
        camundaCmdString := parentDir + "/camunda-zeebe-" + camundaVersion + "/bin/camunda " + extraArgs
        fmt.Println(camundaCmdString)
        camundaCmd := exec.Command(camundaCmdString)
        camundaCmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
        return camundaCmd
}

