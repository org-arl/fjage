build: docs
	PIP_USER=0 python3 -m build

docs: lint
	sphinx-build docs ../../docs/pydocs/

lint:
	python3 -m mypy fjagepy

testupload: build
	twine upload --verbose --repository testpypi dist/*

upload: build
	twine upload --repository pypi dist/*

clean:
	rm -rf dist fjagepy.egg-info
	rm -rf ../../docs/pydocs/*

.PHONY: docs build testupload upload clean

