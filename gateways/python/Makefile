.PHONY: install clean

docs:
	@sed '/^\.\. highlight.*/d' ../../src/sphinx/pythongw.rst > README.rst

testupload: docs
	python setup.py sdist bdist_wheel
	twine upload --repository testpypi dist/*

upload: docs
	python setup.py sdist
	twine upload --repository pypi dist/*

clean:
	find . -name *.pyc -exec rm {} \;
	rm -rf build fjagepy.egg-info dist