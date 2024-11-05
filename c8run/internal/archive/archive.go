package archive

import (
	"archive/tar"
	"archive/zip"
	"compress/gzip"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
)

const OpenFlagsForWriting = os.O_RDWR|os.O_CREATE|os.O_TRUNC
const ReadWriteMode = 0755

func DownloadFile(filepath string, url string) error {
	// if the file already exists locally, don't download a new copy
	_, err := os.Stat(filepath)
	if !errors.Is(err, os.ErrNotExist) {
		return nil
	}

	out, err := os.Create(filepath)
	if err != nil {
		return err
	}
	defer out.Close()

	resp, err := http.Get(url)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("bad status: %s", resp.Status)
	}

	_, err = io.Copy(out, resp.Body)
	if err != nil {
		return err
	}

	fmt.Println("File downloaded successfully to " + filepath)
	return nil
}

func CreateTarGzArchive(files []string, buf io.Writer) error {
	gw := gzip.NewWriter(buf)
	defer gw.Close()
	tw := tar.NewWriter(gw)
	defer tw.Close()

	for _, file := range files {
		err := filepath.Walk(file, func(path string, info os.FileInfo, err error) error {
			if err != nil {
				return err
			}
			if !info.IsDir() {
				return addToArchive(tw, path)
			} else {
				// Add directory to the archive
				header, err := tar.FileInfoHeader(info, path)
				if err != nil {
					return err
				}
				header.Name = path + "/"
				if err := tw.WriteHeader(header); err != nil {
					return err
				}
				return nil

			}
		})
		if err != nil {
			return err
		}
	}

	return nil
}

func ExtractTarGzArchive(filename string, xpath string) error {
	_, err := os.Stat(xpath)
	if !errors.Is(err, os.ErrNotExist) {
		return nil
	}

	tarFile, err := os.Open(filename)
	if err != nil {
		return err
	}
	defer tarFile.Close()

	gz, err := gzip.NewReader(tarFile)
	if err != nil {
		return err
	}
	defer gz.Close()

	absPath, err := filepath.Abs(xpath)

	_, err = os.Stat(absPath)
	if errors.Is(err, os.ErrNotExist) {
		os.Mkdir(absPath, ReadWriteMode)
	}

	tr := tar.NewReader(gz)
	for {
		hdr, err := tr.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			return err
		}
		finfo := hdr.FileInfo()
		fileName := hdr.Name
		absFileName := filepath.Join(absPath, fileName)

		if finfo.Mode().IsDir() {
			if err := os.MkdirAll(absFileName, ReadWriteMode); err != nil {
				return err
			}
			continue
		} else {
			parent := filepath.Dir(absFileName)
			_, err = os.Stat(parent)
			if errors.Is(err, os.ErrNotExist) {
				if err := os.MkdirAll(parent, ReadWriteMode); err != nil {
					return err
				}
			}
		}
		file, err := os.OpenFile(
			absFileName,
			OpenFlagsForWriting,
			finfo.Mode().Perm(),
		)
		if err != nil {
			return err
		}
		fmt.Printf("x %s\n", absFileName)
		n, err := io.Copy(file, tr)
		if closeErr := file.Close(); closeErr != nil {
			return err
		}
		if err != nil {
			return err
		}
		if n != finfo.Size() {
			return fmt.Errorf("wrote %d, want %d", n, finfo.Size())
		}
	}
	return nil
}

func addToArchive(tw *tar.Writer, filename string) error {
	file, err := os.Open(filename)
	if err != nil {
		return err
	}
	defer file.Close()

	info, err := file.Stat()
	if err != nil {
		return err
	}

	header, err := tar.FileInfoHeader(info, info.Name())
	if err != nil {
		return err
	}

	header.Name = filename

	err = tw.WriteHeader(header)
	if err != nil {
		return err
	}

	_, err = io.Copy(tw, file)
	if err != nil {
		return err
	}

	return nil
}

func ZipSource(sources []string, target string) error {
	f, err := os.Create(target)
	if err != nil {
		return err
	}
	defer f.Close()

	writer := zip.NewWriter(f)
	defer writer.Close()

	for _, source := range sources {
		err = filepath.Walk(source, func(path string, info os.FileInfo, err error) error {
			if err != nil {
				return err
			}

			header, err := zip.FileInfoHeader(info)
			if err != nil {
				return err
			}

			header.Method = zip.Deflate

			header.Name, err = filepath.Rel(filepath.Dir(source), path)
			if err != nil {
				return err
			}
			if info.IsDir() {
				header.Name += "/"
			}

			headerWriter, err := writer.CreateHeader(header)
			if err != nil {
				return err
			}

			if info.IsDir() {
				return nil
			}

			f, err := os.Open(path)
			if err != nil {
				return err
			}
			defer f.Close()

			_, err = io.Copy(headerWriter, f)
			return err
		})
		if err != nil {
			return err
		}
	}
	return nil
}

func UnzipSource(source, destination string) error {
	reader, err := zip.OpenReader(source)
	if err != nil {
		return err
	}
	defer reader.Close()

	destination, err = filepath.Abs(destination)
	if err != nil {
		return err
	}

	for _, f := range reader.File {
		err := unzipFile(f, destination)
		if err != nil {
			return err
		}
	}

	return nil
}

func unzipFile(f *zip.File, destination string) error {
	filePath := filepath.Join(destination, f.Name)
	if !strings.HasPrefix(filePath, filepath.Clean(destination)+string(os.PathSeparator)) {
		return fmt.Errorf("invalid file path: %s", filePath)
	}

	if f.FileInfo().IsDir() {
		if err := os.MkdirAll(filePath, os.ModePerm); err != nil {
			return err
		}
		return nil
	}

	if err := os.MkdirAll(filepath.Dir(filePath), os.ModePerm); err != nil {
		return err
	}

	destinationFile, err := os.OpenFile(filePath, OpenFlagsForWriting, f.Mode())
	if err != nil {
		return err
	}
	defer destinationFile.Close()

	zippedFile, err := f.Open()
	if err != nil {
		return err
	}
	defer zippedFile.Close()

	if _, err := io.Copy(destinationFile, zippedFile); err != nil {
		return err
	}
	return nil
}
