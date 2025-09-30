docs:
	sphinx-build docs ../../docs/pydocs/

build: docs
	PIP_USER=0 python3 -m build

testupload: build
	twine upload --repository testpypi dist/*

upload: build
	twine upload --repository pypi dist/*

.PHONY: docs build testupload upload