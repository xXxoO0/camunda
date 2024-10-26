package main

import (
	"c8run/internal/archive"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
)

func Clean(camundaVersion string, elasticsearchVersion string) {
	os.RemoveAll("elasticsearch-" + elasticsearchVersion)
	os.RemoveAll("camunda-zeebe-" + camundaVersion)

	_, err := os.Stat(filepath.Join("log", "camunda.log"))
	if errors.Is(err, os.ErrNotExist) {
		os.Remove(filepath.Join("log", "camunda.log"))
	}
	_, err = os.Stat(filepath.Join("log", "connectors.log"))
	if errors.Is(err, os.ErrNotExist) {
		os.Remove(filepath.Join("log", "connectors.log"))
	}
	_, err = os.Stat(filepath.Join("log", "elasticsearch.log"))
	if errors.Is(err, os.ErrNotExist) {
		os.Remove(filepath.Join("log", "elasticsearch.log"))
	}
}

func downloadAndExtract(filePath, url, extractDir string, extractFunc func(string, string) error) error {
	_, err := os.Stat(filePath)
	if errors.Is(err, os.ErrNotExist) {
		err = archive.DownloadFile(filePath, url)
		if err != nil {
			return err
		}
		fmt.Println("File downloaded successfully to " + filePath)
	}

	_, err = os.Stat(extractDir)
	if errors.Is(err, os.ErrNotExist) {
		err = extractFunc(filePath, ".")
		if err != nil {
			return err
		}
	}
	return nil
}

func PackageWindows(camundaVersion string, elasticsearchVersion string) {
	elasticsearchUrl := "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-" + elasticsearchVersion + "-windows-x86_64.zip"
	elasticsearchFilePath := "elasticsearch-" + elasticsearchVersion + ".zip"
	camundaFilePath := "camunda-zeebe-" + camundaVersion + ".zip"
	camundaUrl := "https://github.com/camunda/camunda/releases/download/" + camundaVersion + "/" + camundaFilePath
	connectorsFilePath := "connector-runtime-bundle-" + camundaVersion + "-with-dependencies.jar"
	connectorsUrl := "https://repo1.maven.org/maven2/io/camunda/connector/connector-runtime-bundle/" + camundaVersion + "/" + connectorsFilePath

	Clean(camundaVersion, elasticsearchVersion)

	err := downloadAndExtract(elasticsearchFilePath, elasticsearchUrl, "elasticsearch-"+elasticsearchVersion, archive.UnzipSource)
	if err != nil {
		panic(err)
	}

	err = downloadAndExtract(camundaFilePath, camundaUrl, "camunda-zeebe-"+camundaVersion, archive.UnzipSource)
	if err != nil {
		panic(err)
	}

	err = downloadAndExtract(connectorsFilePath, connectorsUrl, connectorsFilePath, func(_, _ string) error { return nil })
	if err != nil {
		panic(err)
	}

        os.Chdir("..")
        filesToArchive := []string{
                filepath.Join("c8run", "README.md"),
                filepath.Join("c8run", "connectors-application.properties"),
                filepath.Join("c8run", connectorsFilePath),
                filepath.Join("c8run", "elasticsearch-" + elasticsearchVersion),
                filepath.Join("c8run", "custom_connectors"),
                filepath.Join("c8run", "configuration"),
                filepath.Join("c8run", "c8run.exe"),
                filepath.Join("c8run", "endpoints.txt"),
                filepath.Join("c8run", "log"),
                filepath.Join("c8run", "camunda-zeebe-" + camundaVersion),
        }
	outputArchive, err := os.Create(filepath.Join("c8run", "camunda8-run-" + camundaVersion + "-windows-x86_64.zip"))
        if err != nil {
                panic(err)
        }
        err = archive.CreateTarGzArchive(filesToArchive, outputArchive)
        if err != nil {
                panic(err)
        }
        os.Chdir("c8run")
}

func PackageUnix(camundaVersion string, elasticsearchVersion string) {
	var architecture string
	if runtime.GOARCH == "amd64" {
		architecture = "x86_64"
	} else if runtime.GOARCH == "arm64" {
		architecture = "aarch64"
	}

	elasticsearchUrl := "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-" + elasticsearchVersion + "-" + runtime.GOOS + "-" + architecture + ".tar.gz"
	elasticsearchFilePath := "elasticsearch-" + elasticsearchVersion + ".tar.gz"
	camundaFilePath := "camunda-zeebe-" + camundaVersion + ".tar.gz"
	camundaUrl := "https://github.com/camunda/camunda/releases/download/" + camundaVersion + "/" + camundaFilePath
	connectorsFilePath := "connector-runtime-bundle-" + camundaVersion + "-with-dependencies.jar"
	connectorsUrl := "https://repo1.maven.org/maven2/io/camunda/connector/connector-runtime-bundle/" + camundaVersion + "/" + connectorsFilePath

	Clean(camundaVersion, elasticsearchVersion)

	err := downloadAndExtract(elasticsearchFilePath, elasticsearchUrl, "elasticsearch-"+elasticsearchVersion, archive.ExtractTarGzArchive)
	if err != nil {
		panic(err)
	}

	err = downloadAndExtract(camundaFilePath, camundaUrl, "camunda-zeebe-"+camundaVersion, archive.ExtractTarGzArchive)
	if err != nil {
		panic(err)
	}

	err = downloadAndExtract(connectorsFilePath, connectorsUrl, connectorsFilePath, func(_, _ string) error { return nil })
	if err != nil {
		panic(err)
	}

        os.Chdir("..")
        filesToArchive := []string{
                filepath.Join("c8run", "README.md"),
                filepath.Join("c8run", "connectors-application.properties"),
                filepath.Join("c8run", connectorsFilePath),
                filepath.Join("c8run", "elasticsearch-" + elasticsearchVersion),
                filepath.Join("c8run", "custom_connectors"),
                filepath.Join("c8run", "configuration"),
                filepath.Join("c8run", "c8run"),
                filepath.Join("c8run", "endpoints.txt"),
                filepath.Join("c8run", "log"),
                filepath.Join("c8run", "camunda-zeebe-" + camundaVersion),
        }
	outputArchive, err := os.Create(filepath.Join("c8run", "camunda8-run-" + camundaVersion + "-" + runtime.GOOS + "-" + architecture + ".tar.gz"))
        if err != nil {
                panic(err)
        }
        err = archive.CreateTarGzArchive(filesToArchive, outputArchive)
        if err != nil {
                panic(err)
        }
        os.Chdir("c8run")
}
