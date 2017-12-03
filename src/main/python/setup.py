from setuptools import setup, find_packages

setup(
    name='fjagepy',
    version='1.0',
    description='Fjage Python Gateway',
    author='Subnero Pte. Ltd.',
    author_email='prasad@subnero.com',
    url='https://github.com/org-arl/fjage/tree/dev/src/main/python',
    license='BSD (3-clause)',
    packages=find_packages(exclude=('tests', 'docs')),
)
