docs:
	sphinx-build docs ../../docs/pydocs/

build: docs
	PIP_USER=0 python3 -m build

testupload: build
	twine upload --verbose --repository testpypi dist/*

upload: build
	twine upload --repository pypi dist/*

clean:
	rm -rf dist fjagepy.egg-info
	rm -rf ../../docs/pydocs/*

.PHONY: docs build testupload upload clean

