from setuptools import setup, find_packages

with open('README.rst') as f:
    readme = f.read()

with open('../../../VERSION') as f:
    ver = f.read()
    ver = ver.split('-')[0] + 'b1'

setup(
    name='fjagepy',
    version=ver,
    description='Fjage Python Gateway',
    long_description=readme,
    author='Prasad Anjangi',
    author_email='prasad@subnero.com',
    url='https://github.com/org-arl/fjage/tree/dev/src/main/python',
    license='BSD (3-clause)',
    python_requires='>=3',
    classifiers=[
        'Development Status :: 4 - Beta',
        'Programming Language :: Python :: 3.5',
        'Programming Language :: Python :: 3.6',
    ],
    packages=find_packages(exclude=('tests', 'docs')),
)
